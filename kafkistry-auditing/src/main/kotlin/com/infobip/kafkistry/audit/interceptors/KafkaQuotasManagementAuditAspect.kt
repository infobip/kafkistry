package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.api.InspectApi
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.EntityQuotaManagementAuditEvent
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.setQuotasStatus
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class KafkaQuotasManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver,
    private val inspectApi: InspectApi
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.QuotasManagementApi.*(..))")
    fun actionOnKafkaClusterTopic(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(EntityQuotaManagementAuditEvent())
            .stateCaptor(
                KafkaClusterIdentifier::class.java, QuotaEntityID::class.java,
                stateSupplier = { cluster, entityID ->
                    inspectApi.inspectEntityQuotasOnCluster(entityID, cluster)
                },
                stateApply = { cluster, entityID, before, after ->
                    setQuotasStatus(QuotaEntity.fromID(entityID), cluster, before, after)
                }
            )
            .execute()
    }

}