package com.infobip.kafkistry.audit.interceptors

import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.TopicSensitiveDataAccessAuditEvent
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consume.UnmaskedRecordReadRequest
import com.infobip.kafkistry.service.consume.masking.RecordMaskerFactory
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Aspect
@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class TopicSensitiveDataAccessAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver,
    private val recordMaskerFactory: RecordMaskerFactory,
    private val clustersRegistryService: ClustersRegistryService,
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.api.ConsumeApi.readRecordUnmasked(..))")
    fun auditSensitiveDataAccess(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(TopicSensitiveDataAccessAuditEvent())
            .argumentCaptor(KafkaClusterIdentifier::class.java, 0) { clusterIdentifier = it }
            .argumentCaptor(TopicName::class.java, 1) { topicName = it }
            .argumentCaptor(UnmaskedRecordReadRequest::class.java, 2) { req ->
                partition = req.partition
                offset = req.offset
                reason = req.reason
                val clusterRef = runCatching {
                    clustersRegistryService.getCluster(clusterIdentifier).ref()
                }.getOrNull()
                if (clusterRef != null) {
                    val spec = recordMaskerFactory.maskingSpecFor(topicName, clusterRef)
                    maskedKeyPaths = spec.keyPathDefs
                    maskedValuePaths = spec.valuePathDefs
                    maskedHeaderPaths = spec.headerPathDefs
                }
            }
            .execute()
    }
}
