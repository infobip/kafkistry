package com.infobip.kafkistry.repository

import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier

interface KafkaClustersRepository : RequestingKeyValueRepository<KafkaClusterIdentifier, KafkaCluster>
interface KafkaClustersRefreshableRepository : KafkaClustersRepository, RefreshableRepository

class StorageKafkaClustersRepository(
    delegate: RequestingKeyValueRepository<KafkaClusterIdentifier, KafkaCluster>
) : DelegatingRequestingKeyValueRepository<KafkaClusterIdentifier, KafkaCluster>(delegate), KafkaClustersRefreshableRepository
