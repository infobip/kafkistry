@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.service.kafkastreams.KStreamsAppsProvider
import com.infobip.kafkistry.service.kafkastreams.KafkaStreamsApp
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.sql.SqlDataSource
import org.springframework.stereotype.Component
import java.io.Serializable
import jakarta.persistence.*

@Component
class KStreamAppsDataSource(
    private val kStreamsAppsProvider: KStreamsAppsProvider,
) : SqlDataSource<KStreamApp> {

    override fun modelAnnotatedClass(): Class<KStreamApp> = KStreamApp::class.java

    override fun supplyEntities(): List<KStreamApp> {
        return kStreamsAppsProvider
            .allClustersKStreamApps()
            .flatMap { (clusterIdentifier, kStreamApps) ->
                kStreamApps.map { mapKStreamApp(clusterIdentifier, it) }
            }
    }

    private fun mapKStreamApp(
        clusterIdentifier: KafkaClusterIdentifier, kafkaStreamsApp: KafkaStreamsApp
    ): KStreamApp {
        return KStreamApp().apply {
            id = ClusterKStreamAppID().apply {
                cluster = clusterIdentifier
                kStreamAppId = kafkaStreamsApp.kafkaStreamAppId
            }
            groupId = kafkaStreamsApp.kafkaStreamAppId
            inputTopics = kafkaStreamsApp.inputTopics
            internalTopics = kafkaStreamsApp.kStreamInternalTopics
        }
    }
}


@Embeddable
class ClusterKStreamAppID : Serializable {

    lateinit var cluster: KafkaClusterIdentifier
    lateinit var kStreamAppId: KStreamAppId
}


@Entity
@Table(name = "KStreamApps")
class KStreamApp {

    @EmbeddedId
    lateinit var id: ClusterKStreamAppID

    lateinit var groupId: ConsumerGroupId

    @ElementCollection
    @Column(name = "topic")
    @JoinTable(name = "KStreamApps_InputTopics")
    lateinit var inputTopics: List<TopicName>

    @ElementCollection
    @Column(name = "topic")
    @JoinTable(name = "KStreamApps_InternalTopics")
    lateinit var internalTopics: List<TopicName>

}
