package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.events.EventPublisher
import com.infobip.kafkistry.events.TopicsRepositoryEvent
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.repository.ChangeRequest
import com.infobip.kafkistry.repository.EntityCommitChange
import com.infobip.kafkistry.repository.KafkaTopicsRepository
import com.infobip.kafkistry.repository.storage.Branch
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.history.TopicChange
import com.infobip.kafkistry.service.history.TopicRequest
import com.infobip.kafkistry.service.topic.validation.NamingValidator
import com.infobip.kafkistry.service.topic.validation.check.TopicPreSaveChecker
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service


@Component
@ConfigurationProperties("app.topic-repository")
class TopicsRegistryServiceProperties {
    var enabledCheckers = mutableListOf<Class<in TopicPreSaveChecker>>()
    var disabledCheckers = mutableListOf<Class<in TopicPreSaveChecker>>()
}

@Service
class TopicsRegistryService(
    properties: TopicsRegistryServiceProperties,
    topicsRepository: KafkaTopicsRepository,
    userResolver: CurrentRequestUserResolver,
    eventPublisher: EventPublisher,
    private val namingValidator: NamingValidator,
    allSaveCheckers: List<TopicPreSaveChecker>,
) : AbstractRegistryService<TopicName, TopicDescription, KafkaTopicsRepository, TopicRequest, TopicChange>(
        topicsRepository, userResolver, eventPublisher
) {

    private val saveCheckers = allSaveCheckers
        .filterEnabled(properties.enabledCheckers)
        .filterDisabled(properties.disabledCheckers)

    override fun preCreateCheck(entity: TopicDescription) {
        namingValidator.validateTopicName(entity.name)
        entity.checkRedundantClusterOverrides()
        saveCheckers.forEach { it.preCreateCheck(entity) }
    }

    override fun preUpdateCheck(entity: TopicDescription) {
        entity.checkRedundantClusterOverrides()
        saveCheckers.forEach { it.preUpdateCheck(entity) }
    }

    override fun generateRepositoryEvent(id: TopicName?) = TopicsRepositoryEvent(id)

    override fun mapChangeRequest(id: TopicName, changeRequest: ChangeRequest<TopicDescription>) = TopicRequest(
            branch = changeRequest.branch,
            commitChanges = changeRequest.commitChanges,
            type = changeRequest.type,
            errorMsg = changeRequest.optionalEntity.errorMsg,
            topicName = id,
            topic = changeRequest.optionalEntity.entity
    )

    override fun mapChange(change: EntityCommitChange<TopicName, TopicDescription>) = TopicChange(
            changeType = change.changeType,
            oldContent = change.fileChange.oldContent,
            newContent = change.fileChange.newContent,
            errorMsg = change.optionalEntity.errorMsg,
            topicName = change.id,
            topic = change.optionalEntity.entity
    )

    override val TopicDescription.id: TopicName get() = name
    override val type: Class<TopicDescription> get() = TopicDescription::class.java

    fun createTopic(topicDescription: TopicDescription, updateContext: UpdateContext) = create(topicDescription, updateContext)
    fun deleteTopic(topicName: TopicName, updateContext: UpdateContext) = delete(topicName, updateContext)
    fun updateTopic(topicDescription: TopicDescription, updateContext: UpdateContext) = update(topicDescription, updateContext)

    fun listTopics(): List<TopicDescription> = listAll()
    fun listTopicsAt(branch: Branch): List<TopicDescription> = listAllAt(branch)
    fun findTopic(topicName: TopicName): TopicDescription? = findOne(topicName)
    fun getTopic(topicName: TopicName): TopicDescription = getOne(topicName)

    fun getTopicChanges(topicName: TopicName): List<TopicRequest> = getChanges(topicName)

    private fun TopicDescription.checkRedundantClusterOverrides() {
        perClusterProperties.forEach { (clusterIdentifier, clusterProperties) ->
            if (clusterProperties == properties) {
                throw KafkistryIntegrityException("There is redundant properties override $properties for clusterIdentifier '$clusterIdentifier'")
            }
        }
        perClusterConfigOverrides.forEach { (clusterIdentifier, overrides) ->
            overrides.forEach { (key, value) ->
                if (value == config[key]) {
                    throw KafkistryIntegrityException("There is redundant config override '$key=$value' for clusterIdentifier '$clusterIdentifier'")
                }
            }
        }
    }
}
