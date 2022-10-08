package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.assertAll
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.generator.OverridesMinimizer
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.resources.RequiredResourcesInspector
import com.infobip.kafkistry.service.topic.validation.NamingValidator
import com.infobip.kafkistry.service.topic.validation.TopicConfigurationValidator
import com.infobip.kafkistry.service.topic.wizard.TopicWizardConfigGenerator
import com.infobip.kafkistry.service.topic.wizard.TopicWizardProperties
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.MapAssert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.TimeUnit

internal class OperationSuggestionServiceTest {

    @Test
    fun `test apply same resource requirements for all`() {
        val service = newService(
            clusterRefs =  listOf(ClusterRef("foo"), ClusterRef("bar"), ClusterRef("baz")),
        )
        val topic = newTopic().copy(
            resourceRequirements = ResourceRequirements(
                avgMessageSize = MsgSize(1, BytesUnit.KB),
                messagesRate = MessagesRate(200, ScaleFactor.M, RateUnit.MSG_PER_DAY),
                retention = DataRetention(3, TimeUnit.DAYS),
            )
        )
        val appliedTopic = service.applyResourceRequirements(topic, null, null)
        assertAll {
            assertThat(appliedTopic.config).`as`("Global config")
                .containsEntry("retention.bytes", (1024L * 200 * 1_000_000 * 3).toString())
            assertThat(appliedTopic.perClusterConfigOverrides).isEmpty()
            assertThat(appliedTopic.perTagConfigOverrides).isEmpty()
        }
    }

    @Nested
    inner class MultipleTaggedClusters {

        private val service = newService(
            clusterRefs = listOf(
                ClusterRef("foo", listOf("big", "secret")),
                ClusterRef("bar", listOf("big", "main")),
                ClusterRef("baz", listOf("small")),
                ClusterRef("bat", listOf("small")),
                ClusterRef("xy1", listOf("standard", "secret")),
                ClusterRef("xy2", listOf("standard", "secret")),
                ClusterRef("xy3", listOf("standard", "public")),
            )
        )

        private val oldTopic = newTopic().copy(
            resourceRequirements = ResourceRequirements(
                avgMessageSize = MsgSize(1, BytesUnit.KB),
                messagesRate = MessagesRate(200, ScaleFactor.M, RateUnit.MSG_PER_DAY),
                retention = DataRetention(3, TimeUnit.DAYS),
                messagesRateTagOverrides = mapOf(
                    "big" to MessagesRate(3, ScaleFactor.G, RateUnit.MSG_PER_DAY),
                    "standard" to MessagesRate(100, ScaleFactor.M, RateUnit.MSG_PER_DAY),
                    "small" to MessagesRate(500, ScaleFactor.K, RateUnit.MSG_PER_DAY),
                )
            )
        )

        @Test
        fun `test apply different resource requirements for all`() {
            val newTopic = service.applyResourceRequirements(
                oldTopic, null, null
            )
            assertAll {
                assertThat(newTopic.config).`as`("Global config")
                    .containsEntry("retention.bytes", (1024L * 100 * 1_000_000 * 3).toString())
                assertThat(newTopic.perClusterConfigOverrides).isEmpty()
                assertThat(newTopic.perTagConfigOverrides).hasSize(2)
                assertThat(newTopic.perTagConfigOverrides)
                    .extractingByKey("big")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 3 * 1_000_000_000 * 3).toString())
                assertThat(newTopic.perTagConfigOverrides)
                    .extractingByKey("small")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 500 * 1_000 * 3).toString())
            }
        }

        @Test
        fun `test apply different resource requirements for one tag`() {
            val newTopic = service.applyResourceRequirements(
                oldTopic, null, setOf("big")
            )
            assertAll {
                assertThat(newTopic.config).`as`("Global config").isEmpty()
                assertThat(newTopic.perClusterConfigOverrides).isEmpty()
                assertThat(newTopic.perTagConfigOverrides).hasSize(1)
                assertThat(newTopic.perTagConfigOverrides)
                    .extractingByKey("big")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 3 * 1_000_000_000 * 3).toString())
            }
        }

        @Test
        fun `test apply different resource requirements for one cluster`() {
            val newTopic = service.applyResourceRequirements(
                oldTopic, setOf("xy2"), null
            )
            assertAll {
                assertThat(newTopic.config).`as`("Global config").isEmpty()
                assertThat(newTopic.perTagConfigOverrides).isEmpty()
                assertThat(newTopic.perClusterConfigOverrides).hasSize(1)
                assertThat(newTopic.perClusterConfigOverrides)
                    .extractingByKey("xy2")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 100 * 1_000_000 * 3).toString())
            }
        }

        @Test
        fun `test apply different resource requirements for tag and clusters`() {
            val newTopic = service.applyResourceRequirements(
                oldTopic.copy(config = oldTopic.config.plus("retention.ms" to (3 * 24 * 3600 * 1000L).toString())),
                setOf("baz", "bat"), setOf("standard")
            )
            assertAll {
                assertThat(newTopic.config).`as`("Global config")
                    .containsEntry("retention.ms", (3 * 24 * 3600 * 1000L).toString())
                assertThat(newTopic.perClusterConfigOverrides).isEmpty()
                assertThat(newTopic.perTagConfigOverrides).hasSize(2)
                assertThat(newTopic.perTagConfigOverrides)
                    .extractingByKey("small")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 500 * 1_000 * 3).toString())
                assertThat(newTopic.perTagConfigOverrides)
                    .extractingByKey("standard")
                    .asMapAssert()
                    .containsEntry("retention.bytes", (1024L * 100 * 1_000_000 * 3).toString())
            }
        }
    }

    private fun <K, V, T : Map<K, V>> AbstractObjectAssert<*, T>.asMapAssert(): MapAssert<K, V> {
        @Suppress("UNCHECKED_CAST")
        return this.asInstanceOf(InstanceOfAssertFactories.MAP) as MapAssert<K, V>
    }

    private fun newService(
        clusterRefs: List<ClusterRef> = emptyList(),
        clustersRegistry: ClustersRegistryService = mock<ClustersRegistryService>().apply {
            whenever(listClustersRefs()).thenReturn(clusterRefs)
        },
        topicsRegistryService: TopicsRegistryService = mock(),
        inspectionService: TopicsInspectionService = mock(),
        clusterStateProvider: KafkaClustersStateProvider = mock<KafkaClustersStateProvider>().apply {
            whenever(getLatestClusterState(any())).thenReturn(
                StateData(StateType.UNREACHABLE, "", "", System.currentTimeMillis())
            )
        },
        rulesValidator: TopicConfigurationValidator = mock(),
        configValueInspector: ConfigValueInspector = mock(),
        overridesMinimizer: OverridesMinimizer = OverridesMinimizer(),
        partitionsAssignor: PartitionsReplicasAssignor = PartitionsReplicasAssignor(),
        resourcesInspector: RequiredResourcesInspector = RequiredResourcesInspector(),
        topicWizardConfigGenerator: TopicWizardConfigGenerator = TopicWizardConfigGenerator(
            topicNameGenerator = Optional.empty(),
            minimizer = OverridesMinimizer(),
            namingValidator = NamingValidator(),
            requiredResourcesInspector = RequiredResourcesInspector(),
            properties = TopicWizardProperties(),
        ),
        replicaDirsService: ReplicaDirsService = mock(),
    ): OperationSuggestionService {
        return OperationSuggestionService(
            clustersRegistry,
            topicsRegistryService,
            inspectionService,
            clusterStateProvider,
            rulesValidator,
            configValueInspector,
            overridesMinimizer,
            partitionsAssignor,
            resourcesInspector,
            topicWizardConfigGenerator,
            replicaDirsService
        )
    }

}