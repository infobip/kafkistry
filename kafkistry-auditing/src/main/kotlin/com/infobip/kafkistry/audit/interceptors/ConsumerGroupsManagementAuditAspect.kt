package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ConsumerGroupManagementAuditEvent
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.setConsumerGroup
import com.infobip.kafkistry.kafka.GroupOffsetResetChange
import com.infobip.kafkistry.kafka.GroupOffsetsReset
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class ConsumerGroupsManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.ConsumersApi.deleteClusterConsumerGroup(..))")
    fun deleteConsumeGroup(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            ConsumerGroupManagementAuditEvent(), KafkaClusterIdentifier::class, ConsumerGroupId::class,
            ConsumerGroupManagementAuditEvent::setConsumerGroup
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.ConsumersApi.deleteClusterConsumerGroupOffsets(..))")
    fun deleteConsumeGroupOffsets(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            ConsumerGroupManagementAuditEvent(), KafkaClusterIdentifier::class, ConsumerGroupId::class,
            ConsumerGroupManagementAuditEvent::setConsumerGroup
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.ConsumersApi.resetConsumerGroupOffsets(..))")
    fun resetConsumerGroupOffsets(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(ConsumerGroupManagementAuditEvent())
            .argumentCaptor(KafkaClusterIdentifier::class.java, 0) { clusterIdentifier = it }
            .argumentCaptor(ConsumerGroupId::class.java, 1) { consumerGroupId = it }
            .argumentCaptor(GroupOffsetsReset::class.java, 2) { consumerGroupOffsetsReset = it }
            .resultCaptor(GroupOffsetResetChange::class.java) { consumerGroupOffsetChange = it }
            .execute()
    }

}