package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.PrincipalAclsUpdateAuditEvent
import com.infobip.kafkistry.audit.setPrincipalAcls
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class PrincipalAclsManagementAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.AclsApi.createPrincipalAcls(..))")
    fun createPrincipalAcls(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            PrincipalAclsUpdateAuditEvent(), PrincipalAclRules::class, String::class,
            PrincipalAclsUpdateAuditEvent::setPrincipalAcls,
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.AclsApi.updatePrincipalAcls(..))")
    fun updatePrincipalAcls(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            PrincipalAclsUpdateAuditEvent(), PrincipalAclRules::class, String::class,
            PrincipalAclsUpdateAuditEvent::setPrincipalAcls,
        )
    }

    @Around("execution(* com.infobip.kafkistry.api.AclsApi.deletePrincipalAcls(..))")
    fun deletePrincipalAcls(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executeAndEmitEvent(
            PrincipalAclsUpdateAuditEvent(), PrincipalId::class, String::class
        ) { principalId, msg ->
            principal = principalId
            message = msg
        }
    }

}