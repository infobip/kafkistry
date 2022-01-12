package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.PrincipalAclsManagementAuditEvent
import com.infobip.kafkistry.audit.setPrincipalAclsStatus
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class KafkaPrincipalAclsManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver,
    private val inspectApi: InspectApi
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.AclsManagementApi.*(..))")
    fun actionOnKafkaClusterTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(PrincipalAclsManagementAuditEvent())
            .stateCaptor(
                KafkaClusterIdentifier::class.java, PrincipalId::class.java,
                stateSupplier = { cluster, principal ->
                    inspectApi.inspectPrincipalAclsOnCluster(principal, cluster)
                },
                stateApply = { cluster, principal, before, after ->
                    setPrincipalAclsStatus(principal, cluster, before, after)
                }
            )
            .execute()
    }

}