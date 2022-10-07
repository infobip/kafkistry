package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.ClusterRef
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

/**
 * Use this interface when need to collect/process sampled records
 */
interface RecordSamplingListener {

    /**
     * Signal if this listener needs/wants to sample records from particular cluster / topic / position
     */
    fun need(samplingPosition: SamplingPosition, clusterRef: ClusterRef, topicName: TopicName): Boolean

    /**
     * Return sampler which will be used for one round of sampling from one cluster
     */
    fun sampler(samplingPosition: SamplingPosition, clusterRef: ClusterRef): RecordSampler

    /**
     * Accept notification that cluster has been removed from registry (useful if soe cleanup needs to be made)
     */
    fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier)
}

/**
 * Sampler consumer for one round of sapling from all topic partitions of one cluster
 */
interface RecordSampler {

    /**
     * This function needs to return asap.
     * Avoid long blocking, IO or CPU intensive ops
     * Watch out on memory (big individual records and/or a lot of records)
     */
    fun acceptRecord(consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>)

    fun samplingRoundCompleted() = Unit

    fun samplingRoundFailed(cause: Throwable) = Unit
}

