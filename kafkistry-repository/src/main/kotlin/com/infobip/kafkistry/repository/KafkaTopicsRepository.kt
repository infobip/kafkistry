package com.infobip.kafkistry.repository

import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName

interface KafkaTopicsRepository : RequestingKeyValueRepository<TopicName, TopicDescription>
interface KafkaTopicsRefreshableRepository : KafkaTopicsRepository, RefreshableRepository

class StorageKafkaTopicsRepository(
    delegate: RequestingKeyValueRepository<TopicName, TopicDescription>
) : DelegatingRequestingKeyValueRepository<TopicName, TopicDescription>(delegate), KafkaTopicsRefreshableRepository