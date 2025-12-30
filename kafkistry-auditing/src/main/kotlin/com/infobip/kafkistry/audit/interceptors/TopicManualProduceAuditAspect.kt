package com.infobip.kafkistry.audit.interceptors

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.audit.AbstractManagementAuditAspect
import com.infobip.kafkistry.audit.ManagementEventsListener
import com.infobip.kafkistry.audit.ProduceHeaderInfo
import com.infobip.kafkistry.audit.TopicManualProduceAuditEvent
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.produce.ProduceRequest
import com.infobip.kafkistry.service.produce.ProduceResult
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component

@Aspect
@Component
class TopicManualProduceAuditAspect(
    eventsListener: ManagementEventsListener,
    userResolver: CurrentRequestUserResolver
) : AbstractManagementAuditAspect(eventsListener, userResolver) {

    @Around("execution(* com.infobip.kafkistry.service.produce.KafkaProducerService.produceRecord(..))")
    fun auditProduceRecord(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.executionCapture(TopicManualProduceAuditEvent())
            .argumentCaptor(KafkaClusterIdentifier::class.java, 0) {
                this.clusterIdentifier = it
            }
            .argumentCaptor(TopicName::class.java, 1) {
                this.topicName = it
            }
            .argumentCaptor(ProduceRequest::class.java, 2) { produceRequest ->
                this.keyContent = produceRequest.key?.content
                this.keySerializerType = produceRequest.key?.serializerType
                this.valueContent = produceRequest.value?.content
                this.valueSerializerType = produceRequest.value?.serializerType
                this.headers = produceRequest.headers.map { header ->
                    ProduceHeaderInfo(
                        key = header.key,
                        valueSerializerType = header.value?.serializerType,
                        valueContent = header.value?.content
                    )
                }
                this.partition = produceRequest.partition
                this.timestamp = produceRequest.timestamp
            }
            .resultCaptor(ProduceResult::class.java) { result ->
                if (result != null) {
                    this.produceSuccess = result.success
                    this.producePartition = result.partition
                    this.produceOffset = result.offset
                    this.produceTimestamp = result.timestamp
                    this.errorMessage = result.errorMessage
                }
            }
            .execute()
    }

}
