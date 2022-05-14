package com.infobip.kafkistry.service.topic.validation.rules

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.newClusterInfo
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.topic.configForCluster
import org.junit.Test

class MinInSyncReplicasTooBigRuleTest {

    private val rule = MinInSyncReplicasTooBigRule()

    @Test
    fun `test valid replication-factor=1 min-insync-replicas=null`() {
        rule.assertValid(1, null)
    }

    @Test
    fun `test valid replication-factor=1 min-insync-replicas=1`() {
        rule.assertValid(1, 1)
    }

    @Test
    fun `test invalid replication-factor=1 min-insync-replicas=2`() {
        rule.assertInvalid(1, 2, 1)
    }

    @Test
    fun `test valid replication-factor=2 min-insync-replicas=null`() {
        rule.assertValid(2, null)
    }

    @Test
    fun `test valid replication-factor=2 min-insync-replicas=1`() {
        rule.assertValid(2, 1)
    }

    @Test
    fun `test invalid replication-factor=2 min-insync-replicas=2`() {
        rule.assertInvalid(2, 2, 1)
    }

    @Test
    fun `test invalid replication-factor=2 min-insync-replicas=3`() {
        rule.assertInvalid(2, 3, 1)
    }

    @Test
    fun `test valid replication-factor=5 min-insync-replicas=2`() {
        rule.assertValid(5, 2)
    }

    @Test
    fun `test valid replication-factor=5 min-insync-replicas=7`() {
        rule.assertInvalid(5, 7, 4)
    }

    private fun MinInSyncReplicasTooBigRule.assertValid(replicationFactor: Int, minInSyncReplicas: Int?) {
        assertThat(check(replicationFactor, minInSyncReplicas)).isNull()
    }

    private fun MinInSyncReplicasTooBigRule.assertInvalid(replicationFactor: Int, minInSyncReplicas: Int, expectedFixedMinInsyncReplicas: Int) {
        assertThat(check(replicationFactor, minInSyncReplicas)).isNotNull
        val clusterRef = ClusterRef("identifier", emptyList())
        val fixedTopicDescription = fixConfig(
                topicDescription = newTopic(
                        properties = TopicProperties(1, replicationFactor),
                        config = mapOf("min.insync.replicas" to "$minInSyncReplicas")
                ),
                clusterMetadata = ClusterMetadata(
                    ref = clusterRef,
                    info = newClusterInfo()
                )
        )
        val fixedMinInsyncReplicas = fixedTopicDescription.configForCluster(clusterRef)["min.insync.replicas"]
        assertThat(fixedMinInsyncReplicas).isEqualTo("$expectedFixedMinInsyncReplicas")
    }

    private fun MinInSyncReplicasTooBigRule.check(replicationFactor: Int, minInSyncReplicas: Int?): RuleViolation? {
        return check(
                topicDescriptionView = TopicDescriptionView(
                        name = "",
                        presentOnCluster = true,
                        properties = TopicProperties(1, replicationFactor),
                        config = emptyMap<String, String>().let {
                            if (minInSyncReplicas != null) {
                                it.plus("min.insync.replicas" to "$minInSyncReplicas")
                            } else {
                                it
                            }
                        },
                        originalDescription = newTopic()
                ),
                clusterMetadata = ClusterMetadata(ref = ClusterRef("irrelevant", emptyList()), info = null)
        )
    }
}