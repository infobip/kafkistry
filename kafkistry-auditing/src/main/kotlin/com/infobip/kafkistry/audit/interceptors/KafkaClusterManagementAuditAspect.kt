package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ClusterManagementAuditEvent
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class KafkaClusterManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver,
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.ClustersManagementApi.*(..))")
    fun actionOnKafkaClusterTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(ClusterManagementAuditEvent())
            .argumentCaptor(KafkaClusterIdentifier::class.java, 0) { clusterIdentifier = it }
            .execute()
    }

}