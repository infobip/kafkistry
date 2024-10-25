package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.service.topic.ClusterTopicStatus

fun ClusterTopicStatus.availableActions() = topicClusterStatus.status.availableActions