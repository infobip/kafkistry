package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.EntityQuotaUpdateAuditEvent
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.setQuotas
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class QuotasManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.QuotasApi.createQuotas(..))")
    fun createQuotas(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            EntityQuotaUpdateAuditEvent(), QuotaDescription::class, String::class,
            EntityQuotaUpdateAuditEvent::setQuotas,
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.QuotasApi.updateQuotas(..))")
    fun updateQuotas(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            EntityQuotaUpdateAuditEvent(), QuotaDescription::class, String::class,
            EntityQuotaUpdateAuditEvent::setQuotas,
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.QuotasApi.deleteQuotas(..))")
    fun deleteQuotas(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            EntityQuotaUpdateAuditEvent(), QuotaEntityID::class, String::class
        ) { entityID, msg ->
            quotaEntity = QuotaEntity.fromID(entityID)
            message = msg
        }
    }

}