package com.infobip.kafkistry.it.cluster_ops

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

//extending this way, we don't inherit all other tests
abstract class ClusterAclOperationsTestSuite : AbstractClusterOpsTestSuite() {

    @BeforeEach
    fun `delete all acls`() {
        var existingAcls = doOnKafka { it.listAcls().get() }
        var attempts = 5
        while (existingAcls.isNotEmpty() && attempts >= 0) {
            doOnKafka { it.deleteAcls(existingAcls).get() }
            Thread.sleep(100)
            attempts--
            existingAcls = doOnKafka { it.listAcls().get() }
        }
    }

    @Test
    fun `list create some acls and list them`() {
        val acls = listOf(
                KafkaAclRule(
                        principal = "USER:bob",
                        host = "*",
                        operation = AclOperation(AclOperation.Type.READ, AclOperation.Policy.ALLOW),
                        resource = AclResource(AclResource.Type.TOPIC, "events-", AclResource.NamePattern.PREFIXED)
                ),
                KafkaAclRule(
                        principal = "USER:alice",
                        host = "10.0.0.1",
                        operation = AclOperation(AclOperation.Type.ALL, AclOperation.Policy.DENY),
                        resource = AclResource(AclResource.Type.CLUSTER, "kafka-cluster", AclResource.NamePattern.LITERAL)
                )
        )
        doOnKafka { it.createAcls(acls).get() }
        Awaitility.await("for both acl rules to be created")
                .timeout(5, TimeUnit.SECONDS)
                .until { doOnKafka { it.listAcls().get() }.size >= 2 }

        val existingAcls = doOnKafka { it.listAcls().get() }
        assertThat(existingAcls).containsExactlyInAnyOrder(*acls.toTypedArray())
    }
}