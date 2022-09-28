package com.infobip.kafkistry.service.generator.balance

import com.infobip.kafkistry.kafka.BrokerId

class Balancer {

    fun findRebalanceAction(
        globalState: GlobalState, balanceObjective: BalanceObjective,
        migrationSizeLimitBytes: Long = Long.MAX_VALUE,
        timeoutLimitMs: Long = 5_000
    ): Migrations? {
        val globalContext = globalState.createGlobalContext()
        val avgLoad = globalContext.brokersLoad.average
        val initialLoadDiff = globalContext.brokersLoad.brokersLoadDiff()
        val normalizedDeviation = globalContext.brokersLoad.normalizedLoadDeviation(balanceObjective)

        val sortedBrokers = globalContext.brokersLoad.sortedElements { load, avg ->
            load.score(avg, balanceObjective)
        }
        val brokerLoadTypes = globalContext.brokersLoad.elements.mapValues { (_, load) ->
            load.classifyLoadDiffType(avgLoad, balanceObjective)
        }
        val brokersToOffload = brokerLoadTypes.filterValues { it == LoadDiffType.OVERLOADED || it == LoadDiffType.MIXED }.keys
        val brokersToOnload = brokerLoadTypes.filterValues { it == LoadDiffType.UNDERLOADED || it == LoadDiffType.MIXED }.keys
        if (brokersToOffload.isEmpty() || brokersToOnload.isEmpty()) {
            return null //already optimally balanced for given balanceObjective
        }
        val brokersPairSequence = sequence {
            //use sorted brokers ordering to try maximize chance of finding best migration in time-constrained computations
            val fromBrokers = sortedBrokers.map { it.id }.filter { it in brokersToOffload }.reversed()
            val intoBrokers = sortedBrokers.map { it.id }.filter { it in brokersToOnload }
            // cartesian product for every pair of (from -> into) broker
            for (fromBroker in fromBrokers) {
                for (intoBroker in intoBrokers) {
                    if (fromBroker != intoBroker) {
                        yield(fromBroker to intoBroker)
                    }
                }
            }
        }

        val startTime = System.currentTimeMillis()

        fun Migrations.toResult(newLoadCompute: GlobalContext.(Migrations) -> CollectionLoad<BrokerId, BrokerLoad>): MigrationResult {
            val newBrokersLoad = globalContext.newLoadCompute(this)
            return MigrationResult(
                migrations = this,
                loadDiff = newBrokersLoad.brokersLoadDiff(),
                normalizedDeviation = newBrokersLoad.normalizedLoadDeviation(balanceObjective),
            )
        }

        //migration candidates sorted by estimate score (starting with best towards worst)
        val migrationResultCandidates = brokersPairSequence
            .flatMap { (fromBroker, intoBroker) ->
                globalContext.findMigrations(fromBroker, intoBroker, initialLoadDiff)
            }
            .filter { globalContext.migrationSizeBytes(it) <= migrationSizeLimitBytes }
            .map { migrations ->
                //calculate how much could we gain with this migrations possibility estimate
                migrations.toResult { computeNewBrokersLoadEstimate(it) }
            }
            .sortedBy { it.normalizedDeviation } //smaller diff -> better, process better estimates before worse ones in case there is time limit
            .toList()

        /**
         *  Take a sequence of migration candidates and select one witch produces best improvement (if any?)
         *  while respecting computation time limits
         */
        fun Sequence<MigrationResult>.selectBestMigrations(): MigrationResult? {
            return this
                .takeWhile { System.currentTimeMillis() - startTime <= timeoutLimitMs }
                .map { migrationsResult ->
                    //calculate how much could we gain with this migrations possibility
                    migrationsResult.migrations.toResult { computeNewBrokersLoadActual(it) }    //CPU costly computation
                }
                .filter { it.normalizedDeviation < normalizedDeviation } //filter-out migrations that produce worse disbalance than initial
                .minByOrNull { it.normalizedDeviation }
        }

        // try to select best migration first by taking only migrations with estimate better than current disbalance
        // if there are no better migrations, then fallback to searching migrations with worse estimate
        val bestMigrationsDiff = migrationResultCandidates.asSequence()
            .filter { it.normalizedDeviation < normalizedDeviation }   //try with migrations with estimate better than initial
            .selectBestMigrations()
            ?: migrationResultCandidates.asSequence()
                .filter { it.normalizedDeviation >= normalizedDeviation }  //no promising estimates, fallback to worst candidates by estimate
                .selectBestMigrations()

        return bestMigrationsDiff?.migrations
    }

    data class MigrationResult(
        val migrations: Migrations,
        val loadDiff: BrokerLoad,
        val normalizedDeviation: Double,
    )

    private fun GlobalContext.findMigrations(
        fromBroker: BrokerId, intoBroker: BrokerId, initialLoadDiff: BrokerLoad
    ): List<Migrations> {
        val fromTopicPartitionsLoads = brokerTopicPartitionLoads[fromBroker] ?: return emptyList()
        val intoTopicPartitionsLoads = brokerTopicPartitionLoads[intoBroker] ?: return emptyList()

        val migrations = fromTopicPartitionsLoads
            .filter { it.key !in intoTopicPartitionsLoads.keys }    //can't move partition which is already there
            .flatMap { (topicPartition, partitionLoad) ->
                val partitionAsBrokerLoad = partitionLoad.toMinimumBrokerLoad()
                tryMigrate(topicPartition, partitionAsBrokerLoad, fromBroker, intoBroker, initialLoadDiff)
            }
        val leadershipSwaps = fromTopicPartitionsLoads.keys
            .filter { it in intoTopicPartitionsLoads.keys }
            .let { tryLeaderSwaps(it, fromBroker, intoBroker) }

        return migrations + leadershipSwaps
    }

    private fun GlobalContext.tryMigrate(
        topicPartition: TopicPartition,
        partitionAsBrokerLoad: BrokerLoad,
        fromBroker: BrokerId,
        intoBroker: BrokerId,
        initialLoadDiff: BrokerLoad,
    ): List<Migrations> {
        val topicAssignments = topicsAssignments[topicPartition.topic]?.partitionAssignments ?: return emptyList()
        val partitionReplicas = topicAssignments[topicPartition.partition] ?: return emptyList()
        val fromTopicPartitions = brokersAssignments[fromBroker]?.topics?.get(topicPartition.topic) ?: emptyList()
        val intoTopicPartitions = brokersAssignments[intoBroker]?.topics?.get(topicPartition.topic) ?: emptyList()
        val partitionMigration = PartitionReAssignment(topicPartition, partitionReplicas, partitionReplicas.minus(fromBroker).plus(intoBroker))
        return if (intoTopicPartitions.isEmpty()) {
            if (isMigrationLoadWithinBounds(initialLoadDiff, partitionAsBrokerLoad)) {
                //destination intoBroker has no partitions of this topic, no need for reverse migrations
                listOf(Migrations(partitionMigration))
            } else {
                emptyList()
            }
        } else {
            intoTopicPartitions
                .filter { it !in fromTopicPartitions }  //can't do reverse move if partition is already there
                .filter {
                    val reverseMigrateLoad = brokerTopicPartitionLoads[intoBroker]
                        ?.get(TopicPartition(topicPartition.topic, it))
                        ?.toMinimumBrokerLoad()
                        ?: BrokerLoad.ZERO
                    isMigrationLoadWithinBounds(initialLoadDiff, partitionAsBrokerLoad, reverseMigrateLoad)
                }
                .map { partition ->
                    //add reverse migration to keep assignments of one topic balanced by itself
                    val reversePartitionReplicas = topicAssignments[partition] ?: return emptyList()
                    val reversePartitionMigration = PartitionReAssignment(
                        TopicPartition(topicPartition.topic, partition),
                        reversePartitionReplicas,
                        reversePartitionReplicas.minus(intoBroker).plus(fromBroker)
                    )
                    Migrations(partitionMigration, reversePartitionMigration)
                }
                .let { migrations ->
                    if (intoTopicPartitions.size < fromTopicPartitions.size) {
                        //allow migration with no reverse move if destination broker has less partitions than source
                        migrations + Migrations(partitionMigration)
                    } else {
                        migrations
                    }
                }
        }
    }

    // use leader=false and rf=0 to generate lower bound of this topic-partition's impact on broker
    private fun PartitionLoad.toMinimumBrokerLoad() = toBrokerLoad(false, 0)

    private fun isMigrationLoadWithinBounds(
        initialLoadDiff: BrokerLoad,
        migrateLoad: BrokerLoad,
        reverseMigrateLoad: BrokerLoad = BrokerLoad.ZERO
    ): Boolean {
        //return true
        val netMigrationLoad = migrateLoad - reverseMigrateLoad
        if (netMigrationLoad.isAllNegativeOrZero()) {
            //don't allow opposite of what we are trying to do (load even more overloaded broker and unload underloaded broker)
            return false
        }
        val diffGainLoad = initialLoadDiff - netMigrationLoad
        val tooMuch = diffGainLoad.isAllNegativeOrZero()
        return !tooMuch   //migration would add more load to destination broker than current disbalance
    }

    private fun BrokerLoad.isAllNegativeOrZero(): Boolean {
        return size <= 0 && rate <= 0 && consumeRate <= 0 && replicationRate <= 0 && replicas <= 0 && leaders <= 0
    }

    private fun GlobalContext.tryLeaderSwaps(
        topicPartitions: Iterable<TopicPartition>,
        fromBroker: BrokerId,
        intoBroker: BrokerId
    ): List<Migrations> {
        return topicPartitions
            .mapNotNull { topicPartition ->
                topicsAssignments[topicPartition.topic]?.partitionAssignments?.get(topicPartition.partition)?.let {
                    topicPartition to it
                }
            }
            .filter { (_, replicas) -> replicas.indexOf(fromBroker) == 0 && intoBroker in replicas }
            .map { (topicPartition, replicas) ->
                val newReplicas = listOf(intoBroker) + replicas.minus(intoBroker)
                Migrations(PartitionReAssignment(topicPartition, replicas, newReplicas))
            }
    }
}
