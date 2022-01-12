package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.TopicUpdateAuditEvent
import com.infobip.kafkistry.audit.setTopic
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class TopicDescriptionsManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.TopicsApi.createTopic(..))")
    fun createTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            TopicUpdateAuditEvent(), TopicDescription::class, String::class, TopicUpdateAuditEvent::setTopic
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.TopicsApi.updateTopic(..))")
    fun updateTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            TopicUpdateAuditEvent(), TopicDescription::class, String::class, TopicUpdateAuditEvent::setTopic
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.TopicsApi.deleteTopic(..))")
    fun deleteTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            TopicUpdateAuditEvent(), String::class, String::class
        ) { topic, msg ->
            topicName = topic
            message = msg
        }
    }

}