package com.infobip.kafkistry.metric

import com.infobip.kafkistry.model.KafkaClusterIdentifier

interface ClusterMetricLabelProvider {
    fun labelName(): String
    fun labelValue(clusterIdentifier: KafkaClusterIdentifier): String
}

class DefaultClusterMetricLabelProvider: ClusterMetricLabelProvider {
    override fun labelName(): String = "cluster"
    override fun labelValue(clusterIdentifier: KafkaClusterIdentifier): String = clusterIdentifier
}

