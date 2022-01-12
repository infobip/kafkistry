package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafkastate.KafkaClusterState
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicCreationWizardAnswers
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicNameMetadata
import com.infobip.kafkistry.service.topic.wizard.TopicWizardConfigGenerator
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import org.springframework.stereotype.Service
import java.util.*

@Service
class TopicWizardService(
    private val topicWizardConfigGenerator: TopicWizardConfigGenerator,
    private val clustersRegistryService: ClustersRegistryService,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
    topicPostProcessor: Optional<TopicWizardPostProcessor>,
) {

    private val postProcessor = topicPostProcessor.orElse(TopicWizardPostProcessor.NONE)

    fun generateTopicDescriptionFromWizardAnswers(
            topicCreationWizardAnswers: TopicCreationWizardAnswers
    ): TopicDescription {
        val allClusterStates = clustersRegistryService.listClustersRefs().associateWith {
            kafkaClustersStateProvider.getLatestClusterState(it.identifier)
        }
        val generatedTopicDescription = topicWizardConfigGenerator.generateTopicDescription(
            topicCreationWizardAnswers, allClusterStates
        )
        return postProcessor.postProcess(generatedTopicDescription, topicCreationWizardAnswers, allClusterStates)
    }

    fun generateTopicName(topicNameMetadata: TopicNameMetadata): String = topicWizardConfigGenerator.generateName(topicNameMetadata)
}

/**
 * Allows customization/postprocessing of generated [TopicDescription].
 * Useful in scenarios when having custom [com.infobip.kafkistry.service.validation.rules.ValidationRule] which might
 * mandate specific restrictions on some config properties of topic, implementation of [TopicWizardPostProcessor] could
 * then make adjustments in [TopicDescription] to satisfy such requirements.
 */
interface TopicWizardPostProcessor {

    fun postProcess(
        generatedTopicDescription: TopicDescription,
        topicCreationWizardAnswers: TopicCreationWizardAnswers,
        allClusterStates: Map<ClusterRef, StateData<KafkaClusterState>>,
    ): TopicDescription

    companion object {
        val NONE = object : TopicWizardPostProcessor {
            override fun postProcess(
                generatedTopicDescription: TopicDescription,
                topicCreationWizardAnswers: TopicCreationWizardAnswers,
                allClusterStates: Map<ClusterRef, StateData<KafkaClusterState>>
            ): TopicDescription = generatedTopicDescription
        }
    }
}