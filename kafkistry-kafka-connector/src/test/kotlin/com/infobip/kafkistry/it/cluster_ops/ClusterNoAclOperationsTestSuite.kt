package com.infobip.kafkistry.it.cluster_ops

import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.common.errors.SecurityDisabledException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

//extending this way, we append test here to base test suite
abstract class ClusterNoAclOperationsTestSuite : ClusterOperationsTestSuite() {

    @Test
    fun `list acls - fail because security is disabled`() {
         assertThatThrownBy {
            doOnKafka { it.listAcls().get() }
        }.cause()
                .isInstanceOf(KafkaClusterManagementException::class.java)
                .extracting<Throwable> { it.cause }
                .isInstanceOf(SecurityDisabledException::class.java)
    }

}