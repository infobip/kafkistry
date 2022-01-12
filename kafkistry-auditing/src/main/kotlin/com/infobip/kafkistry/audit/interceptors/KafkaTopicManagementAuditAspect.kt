package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.TopicManagementAuditEvent
import com.infobip.kafkistry.audit.setKafkaTopicStatus
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class KafkaTopicManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver,
    private val inspectApi: InspectApi
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.ManagementApi.*(..))")
    fun actionOnKafkaClusterTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(TopicManagementAuditEvent())
            .stateCaptor(
                KafkaClusterIdentifier::class.java, TopicName::class.java,
                stateSupplier = { cluster, topic ->
                    inspectApi.inspectTopicOnCluster(topic, cluster)
                },
                stateApply = { cluster, topic, before, after ->
                    setKafkaTopicStatus(topic, cluster, before, after)
                }
            )
            .execute()
    }

}