<div>
    <button role="button" data-toggle="collapse" data-target="#incremental-balancing-doc"
            class="btn btn-sm btn-outline-info mb-2">
        Incremental balancing documentation...
    </button>
    <div id="incremental-balancing-doc" class="collapse">
        <p>
            <strong>NOTE</strong>: consider doing incremental balancing if all topics are "balanced" on their own
            (having equal number of partition replicas and partition leaders per each broker)
        </p>

        <h5>
            Why incremental balancing?
        </h5>
        <ul>
            <li><strong>End goal</strong>: achieving "near-equal" load on all brokers in cluster
                <ul>
                    <li>Similar disk usage on all brokers</li>
                    <li>Similar network inbound on all brokers</li>
                    <li>Similar network outbound on all brokers</li>
                </ul>
            </li>
            <li>Even if all topics are "balanced" on their own, there is still possibility that
                all partitions of some topic doesn't have equal amount of traffic going through which may cause
                uneven impact on brokers in cluster
            </li>
            <li>All topics may not have partition-count which is multiple of number of brokers in cluster,
                which will have uneven load impact on different brokers
            </li>
        </ul>

        <h5>
            Why incremental instead computing optimal assignments for all topics and applying it in one step?
        </h5>
        <ul>
            <li>Computing optimal assignments is nearly impossible when having large number of topic-partitions</li>
            <li>Even if there was an algorithm which could do that optimization problem fast,
                there would still be some problems with such approach:
                <ul>
                    <li>Amount of traffic per topic-partition is likely to change in the future, so an optimal
                        assignment now may not be optimal "tomorrow"
                    </li>
                    <li>Optimal assignments are likely "very different" than current assignments which
                        applying would introduce a lot of stress on cluster when needing to move a large number of
                        partition replicas across cluster
                    </li>
                </ul>
            </li>
            <li>Due to issues mentioned above, there is no point chasing an optimal load distribution across brokers
            </li>
            <li>Near similar load per different brokers should be good enough in most circumstances</li>
        </ul>
        <h5>
            When to do incremental balancing?
        </h5>
        <ul>
            <li>When all topics are balanced on their own but there is still significant dis-balance of load across
                brokers
            </li>
        </ul>
        <h5>
            What incremental balancing does?
        </h5>
        <ul>
            <li>
                Algorithm receives current state of assignments and loads as input
            </li>
            <li>
                Output of one iteration is:
                <ul>
                    <li>migration of one partition replica</li>
                    <li>swap migrations of two partition replicas</li>
                    <li>change on preferred leader replica of particular topic-partition</li>
                </ul>
            </li>
            <li>
                Resulting proposed change of current assignments is best found change (found within the time limit)
                such that it contributes to overall cluster balance the most (optimizing fo given balance objective)
            </li>
            <li>
                It is possible to compute multiple such iterations and apply them all at once
            </li>
            <li>
                After change is applied, you need to wait for migrations to complete and then verify new assignments
                to remove generated throttles
            </li>
            <li>
                If cluster disbalance is still too high, this process can be repeated again
            </li>
        </ul>
    </div>
</div>
