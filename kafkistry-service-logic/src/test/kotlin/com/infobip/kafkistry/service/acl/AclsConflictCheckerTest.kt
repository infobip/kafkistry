package com.infobip.kafkistry.service.acl

import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.Presence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AclsConflictCheckerTest {

    private val clusterIdentifier = "kfk-acl"

    private fun newResolverOf(vararg acls: String): AclsConflictResolver {
        val kafkaAcls = acls.map { it.parseAcl() }
        val clusterData = AclClusterLinkData(
            clusterRef = ClusterRef(clusterIdentifier),
            acls = kafkaAcls,
            topics = emptyList(),
            consumerGroups = emptyList(),
            quotaEntities = emptyList(),
        )

        return AclsConflictResolver(object : AclResolverDataProvider {
            override fun getClustersData(): Map<KafkaClusterIdentifier, AclClusterLinkData> {
                return mapOf(clusterIdentifier to clusterData)
            }
        })
    }

    private fun AclsConflictResolver.addingGroupConflicts(acl: String): List<String> {
        return checker(withAcls = mapOf(acl.parseAcl() to Presence.ALL))
            .consumerGroupConflicts(acl.parseAcl(), clusterIdentifier)
            .map { it.asString() }
    }

    private fun AclsConflictResolver.addingTransactionalConflicts(acl: String): List<String> {
        return checker(withAcls = mapOf(acl.parseAcl() to Presence.ALL))
            .transactionalIdConflicts(acl.parseAcl(), clusterIdentifier)
            .map { it.asString() }
    }

    @Test
    fun `test nothing on empty`() {
        val checker = newResolverOf()
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:g READ ALLOW")
        assertThat(conflicts).isEmpty()
    }

    @Test
    fun `test one conflicting group acl`() {
        val checker = newResolverOf(
            "User:other * GROUP:conflict READ ALLOW",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:conflict READ ALLOW")
        assertThat(conflicts).containsExactlyInAnyOrder(
            "User:other * GROUP:conflict READ ALLOW",
        )
    }

    @Test
    fun `test no conflicting groups`() {
        val checker = newResolverOf(
            "User:other * GROUP:other READ ALLOW",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:conflict READ ALLOW")
        assertThat(conflicts).isEmpty()
    }

    @Test
    fun `test existing deny is not conflicting`() {
        val checker = newResolverOf(
            "User:other * GROUP:other READ DENY",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:other READ ALLOW")
        assertThat(conflicts).isEmpty()
    }

    @Test
    fun `test deny is not conflicting with existing`() {
        val checker = newResolverOf(
            "User:other * GROUP:other READ ALLOW",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:other READ DENY")
        assertThat(conflicts).isEmpty()
    }

    @Test
    fun `test prefix against existing`() {
        val checker = newResolverOf(
            "User:a * GROUP:g1 READ ALLOW",
            "User:b * GROUP:g2 READ ALLOW",
            "User:c * GROUP:other READ ALLOW",
        )
        Math.sqrt(2.0)
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:g* READ ALLOW")
        assertThat(conflicts).containsExactlyInAnyOrder(
            "User:a * GROUP:g1 READ ALLOW",
            "User:b * GROUP:g2 READ ALLOW",
        )
    }

    @Test
    fun `test literal against existing prefix`() {
        val checker = newResolverOf(
            "User:a * GROUP:gr* READ ALLOW",
            "User:b * GROUP:sec* READ ALLOW",
            "User:c * GROUP:other READ ALLOW",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:group READ ALLOW")
        assertThat(conflicts).containsExactlyInAnyOrder(
            "User:a * GROUP:gr* READ ALLOW",
        )
    }

    @Test
    fun `test exising prefixed denied by literal`() {
        val checker = newResolverOf(
            "User:a * GROUP:gr* READ ALLOW",
            "User:a * GROUP:group READ DENY",
        )
        val conflicts1 = checker.addingGroupConflicts("User:x * GROUP:group READ ALLOW")
        assertThat(conflicts1).isEmpty()
        val conflicts2 = checker.addingGroupConflicts("User:x * GROUP:group2 READ ALLOW")
        assertThat(conflicts2).containsExactlyInAnyOrder(
            "User:a * GROUP:gr* READ ALLOW",
        )
    }

    @Test
    fun `test add prefixed denied by literal`() {
        val checker = newResolverOf(
            "User:a * GROUP:group READ ALLOW",
            "User:b * GROUP:group2 READ ALLOW",
            "User:x * GROUP:group READ DENY",
        )
        val conflicts = checker.addingGroupConflicts("User:x * GROUP:gr* READ ALLOW")
        assertThat(conflicts).containsExactlyInAnyOrder(
            "User:b * GROUP:group2 READ ALLOW",
        )
    }

    @Test
    fun `test conflict on prefixed transactional id`() {
        val checker = newResolverOf(
            "User:x * TRANSACTIONAL_ID:x* WRITE ALLOW",
        )
        val conflicts = checker.addingTransactionalConflicts("User:x-1 * TRANSACTIONAL_ID:x-1* ALL ALLOW")
        assertThat(conflicts).containsExactlyInAnyOrder(
            "User:x * TRANSACTIONAL_ID:x* WRITE ALLOW",
        )
    }
}