package com.infobip.kafkistry.it.cluster_ops

import org.apache.kafka.common.errors.SecurityDisabledException
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

//extending this way, we append test here to base test suite
abstract class ClusterNoAclOperationsTestSuite : ClusterOperationsTestSuite() {

    @Test
    fun `list acls - fail because security is disabled`() {
        val exception = assertFailsWith<ExecutionException> {
            doOnKafka {
                it.listAcls().get()
            }
        }
        assertThat(exception.cause)
                .isInstanceOf(KafkaClusterManagementException::class.java)
                .extracting<Throwable> { it.cause }
                .isInstanceOf(SecurityDisabledException::class.java)
    }

}