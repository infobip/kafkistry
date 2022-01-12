package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ClusterUpdateAuditEvent
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.setCluster
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class ClustersManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.ClustersApi.addCluster(*))")
    fun addCluster(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            ClusterUpdateAuditEvent(), KafkaCluster::class, String::class, ClusterUpdateAuditEvent::setCluster
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.ClustersApi.updateCluster(*))")
    fun updateCluster(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            ClusterUpdateAuditEvent(), KafkaCluster::class, String::class, ClusterUpdateAuditEvent::setCluster
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.ClustersApi.removeCluster(*))")
    fun removeCluster(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(ClusterUpdateAuditEvent(), String::class, String::class) { cluster, msg ->
            clusterIdentifier = cluster
            message = msg
        }
    }

}