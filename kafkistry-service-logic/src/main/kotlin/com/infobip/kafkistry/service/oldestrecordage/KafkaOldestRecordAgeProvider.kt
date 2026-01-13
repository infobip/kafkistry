package com.infobip.kafkistry.service.oldestrecordage

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.kafka.SamplingPosition
import com.infobip.kafkistry.kafkastate.OldestRecordsAges
import com.infobip.kafkistry.kafka.RecordSampler
import com.infobip.kafkistry.kafka.RecordSamplingListener
import com.infobip.kafkistry.kafka.SamplerState
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.utils.ClusterTopicFilter
import com.infobip.kafkistry.utils.ClusterTopicFilterProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
@ConfigurationProperties("app.oldest-record-age")
class OldestRecordAgeProviderProperties {
    var enabled = true

    @NestedConfigurationProperty
    var enabledOn = ClusterTopicFilterProperties()
}

@Component
@ConditionalOnProperty("app.oldest-record-age.enabled", matchIfMissing = true)
class KafkaOldestRecordAgeProvider(
    properties: OldestRecordAgeProviderProperties,
    recordTimestampExtractor: Optional<RecordTimestampExtractor>,
) : RecordSamplingListener<OldestRecordsAges> {

    private val sampleFilter = ClusterTopicFilter(properties.enabledOn)
    private val timestampExtractor = recordTimestampExtractor.orElse(RecordTimestampExtractor.NONE)
    private val clusterStates: MutableMap<KafkaClusterIdentifier, OldestRecordsAges> = ConcurrentHashMap()

    override fun need(
        samplingPosition: SamplingPosition, clusterRef: ClusterRef, topicName: TopicName
    ): Boolean {
        return samplingPosition == SamplingPosition.OLDEST && sampleFilter(clusterRef, topicName)
    }

    override fun sampler(samplingPosition: SamplingPosition, clusterRef: ClusterRef): RecordSampler<OldestRecordsAges> {
        return TimestampRecordVisitor(clusterRef)
    }

    override fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) {
        clusterStates.remove(clusterIdentifier)
    }

    override fun updateState(clusterRef: ClusterRef, state: OldestRecordsAges?) {
        if (state == null) {
            clusterStates.remove(clusterRef.identifier)
        } else {
            clusterStates[clusterRef.identifier] = state
        }
    }

    override fun sampledState(clusterIdentifier: KafkaClusterIdentifier): SamplerState<OldestRecordsAges> {
        return SamplerState(KafkaOldestRecordAgeProvider::class.java, clusterStates[clusterIdentifier])
    }

    fun getLatestState(kafkaClusterIdentifier: KafkaClusterIdentifier): OldestRecordsAges? {
        return clusterStates[kafkaClusterIdentifier]
    }

    fun getAllLatestStates(): Map<KafkaClusterIdentifier, OldestRecordsAges> {
        return clusterStates.toMap()
    }

    private inner class TimestampRecordVisitor(
        private val clusterRef: ClusterRef,
    ) : RecordSampler<OldestRecordsAges> {

        private val timestampSamples = ArrayList<Triple<TopicName, Partition, Long>>()

        override fun acceptRecord( consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>) {
            with(consumerRecord) {
                val timestamp = timestampExtractor.extractTimestampFrom(this) ?: timestamp()
                timestampSamples.add(Triple(topic(), partition(), timestamp))
            }
        }

        override fun samplingRoundCompleted() = wrapUp()

        override fun samplingRoundFailed(cause: Throwable) {
            if (timestampSamples.isEmpty()) {
                clusterStates.remove(clusterRef.identifier)
            } else {
                wrapUp()
            }
        }

        fun wrapUp() {
            val now = System.currentTimeMillis()
            val earliestRecordAges = timestampSamples
                .groupBy { it.first }
                .mapValues {
                    it.value.associate { (_, partition, timestamp) ->
                        partition to (now - timestamp)
                    }
                }
            clusterStates[clusterRef.identifier] = OldestRecordsAges(earliestRecordAges)
        }
    }

}
