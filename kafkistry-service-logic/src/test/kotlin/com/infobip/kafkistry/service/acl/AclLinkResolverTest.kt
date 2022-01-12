package com.infobip.kafkistry.service.acl

import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.kafka.asString
import com.infobip.kafkistry.kafka.parseAcl
import org.junit.Test

class AclLinkResolverTest {

    @Test
    fun `test empty links`() {
        val resolver = newResolverWithClusterData()
        assertThat(resolver.findTopicAffectingAclRules("foo")).isEmpty()
        assertThat(resolver.findGroupAffectingAclRules("bar")).isEmpty()
        assertThat(resolver.findAffectedTopics("x * TOPIC:y ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("x * GROUP:y ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectingQuotaEntities("User:x")).isEmpty()
        assertThat(resolver.findAffectedPrincipals("bob|<default>")).isEmpty()
    }

    @Test
    fun `test acl targeting single topic`() {
        val resolver = newResolverWithClusterData(
                topics = listOf("foo", "bar"),
                acls = listOf(
                        "p * TOPIC:foo ALL ALLOW",
                        "p * TOPIC:baz READ DENY"
                )
        )
        assertThat(resolver.findAffectedTopics("p * TOPIC:foo ALL ALLOW")).containsExactly("foo")
        assertThat(resolver.findAffectedTopics("p * TOPIC:baz ALL ALLOW")).containsExactly("baz")
        assertThat(resolver.findTopicAffectingAclRules("foo")).containsExactly("p * TOPIC:foo ALL ALLOW")
        assertThat(resolver.findTopicAffectingAclRules("bar")).isEmpty()
        assertThat(resolver.findTopicAffectingAclRules("baz")).containsExactly("p * TOPIC:baz READ DENY")

        assertThat(resolver.findAffectedGroups("p * TOPIC:foo ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("p * TOPIC:baz ALL ALLOW")).isEmpty()
        assertThat(resolver.findGroupAffectingAclRules("foo")).isEmpty()
        assertThat(resolver.findGroupAffectingAclRules("bar")).isEmpty()
        assertThat(resolver.findGroupAffectingAclRules("baz")).isEmpty()
    }

    @Test
    fun `test prefixed acl targeting multiple topic`() {
        val resolver = newResolverWithClusterData(
                topics = listOf("foo", "bar", "baz"),
                acls = listOf(
                        "p * TOPIC:ba* READ ALLOW"
                )
        )
        assertThat(resolver.findAffectedTopics("p * TOPIC:ba* READ ALLOW")).containsExactly("bar", "baz")
        assertThat(resolver.findAffectedTopics("p * TOPIC:foo ALL ALLOW")).containsExactly("foo")
        assertThat(resolver.findAffectedTopics("p * TOPIC:t* READ ALLOW")).isEmpty()
        assertThat(resolver.findTopicAffectingAclRules("foo")).isEmpty()
        assertThat(resolver.findTopicAffectingAclRules("bar")).containsExactly("p * TOPIC:ba* READ ALLOW")
        assertThat(resolver.findTopicAffectingAclRules("baz")).containsExactly("p * TOPIC:ba* READ ALLOW")
    }

    @Test
    fun `test wildcard acl targeting multiple topic`() {
        val resolver = newResolverWithClusterData(
                topics = listOf("foo", "bar", "baz"),
                acls = listOf(
                        "p 10.0.0.1 TOPIC:* ALL DENY"
                )
        )
        assertThat(resolver.findAffectedTopics("p 10.0.0.1 TOPIC:* ALL DENY")).containsExactly("foo", "bar", "baz")
        assertThat(resolver.findAffectedTopics("p * TOPIC:* ALL ALLOW")).containsExactly("foo", "bar", "baz")
        assertThat(resolver.findAffectedTopics("p * GROUP:* READ ALLOW")).isEmpty()
        assertThat(resolver.findTopicAffectingAclRules("foo")).containsExactly("p 10.0.0.1 TOPIC:* ALL DENY")
        assertThat(resolver.findTopicAffectingAclRules("bar")).containsExactly("p 10.0.0.1 TOPIC:* ALL DENY")
        assertThat(resolver.findTopicAffectingAclRules("baz")).containsExactly("p 10.0.0.1 TOPIC:* ALL DENY")
    }

    @Test
    fun `test default user targeting multiple principals`() {
        val resolver = newResolverWithClusterData(
                quotaEntities = listOf(QuotaEntity.userDefault()),
                acls = listOf(
                        "User:bob * TOPIC:* ALL ALLOW",
                        "User:alice * TOPIC:* ALL ALLOW",
                )
        )
        assertThat(resolver.findAffectedPrincipals("<default>|<all>")).containsExactly("User:bob", "User:alice")
        assertThat(resolver.findAffectedPrincipals("bob|<default>")).containsExactly("User:bob")
        assertThat(resolver.findAffectedPrincipals("mike|<all>")).isEmpty()
        assertThat(resolver.findAffectingQuotaEntities("User:bob")).containsExactly("<default>|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:alice")).containsExactly("<default>|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:mike")).containsExactly("<default>|<all>")
    }

    @Test
    fun `test literal user targeting one principal`() {
        val resolver = newResolverWithClusterData(
                quotaEntities = listOf(QuotaEntity.user("bob")),
                acls = listOf(
                        "User:bob * TOPIC:* ALL ALLOW",
                        "User:alice * TOPIC:* ALL ALLOW",
                )
        )
        assertThat(resolver.findAffectedPrincipals("bob|<all>")).containsExactly("User:bob")
        assertThat(resolver.findAffectedPrincipals("bob|<default>")).containsExactly("User:bob")
        assertThat(resolver.findAffectedPrincipals("<default>|<all>")).containsExactly("User:alice")
        assertThat(resolver.findAffectedPrincipals("mike|<all>")).isEmpty()
        assertThat(resolver.findAffectingQuotaEntities("User:bob")).containsExactly("bob|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:alice")).isEmpty()
        assertThat(resolver.findAffectingQuotaEntities("User:mike")).isEmpty()
    }

    @Test
    fun `complex setup`() {
        val resolver = newResolverWithClusterData(
                topics = listOf(
                        "logins",
                        "payment-books", "payment-movies",
                        "audit-views", "audit-errors"
                ),
                groups = listOf(
                        "auth",
                        "billing", "billing-report",
                        "logging"
                ),
                quotaEntities = listOf(
                        QuotaEntity.user("debugger"),
                        QuotaEntity.user("unrelated"),
                        QuotaEntity.userDefault(),
                        QuotaEntity.client("some-client"),
                ),
                acls = listOf(
                        "User:security 10.0.0.1 TOPIC:logins READ ALLOW",
                        "User:security 10.0.0.1 GROUP:auth ALL ALLOW",
                        "User:charging * TOPIC:payment* ALL ALLOW",
                        "User:charging * GROUP:billing ALL ALLOW",
                        "User:finance-stats * TOPIC:payment* READ ALLOW",
                        "User:finance-stats * GROUP:billing-report ALL ALLOW",
                        "User:debugger * TOPIC:* READ ALLOW",
                        "User:debugger * TOPIC:logins ALL DENY",
                        "User:debugger * GROUP:logging ALL ALLOW"
                )
        )

        //check which rules affects each topic
        assertThat(resolver.findTopicAffectingAclRules("logins")).containsExactly(
                "User:debugger * TOPIC:* READ ALLOW",
                "User:security 10.0.0.1 TOPIC:logins READ ALLOW",
                "User:debugger * TOPIC:logins ALL DENY"
        )
        assertThat(resolver.findTopicAffectingAclRules("payment-books")).containsExactly(
                "User:debugger * TOPIC:* READ ALLOW",
                "User:charging * TOPIC:payment* ALL ALLOW",
                "User:finance-stats * TOPIC:payment* READ ALLOW"
        )
        assertThat(resolver.findTopicAffectingAclRules("payment-movies")).containsExactly(
                "User:debugger * TOPIC:* READ ALLOW",
                "User:charging * TOPIC:payment* ALL ALLOW",
                "User:finance-stats * TOPIC:payment* READ ALLOW"
        )
        assertThat(resolver.findTopicAffectingAclRules("audit-views")).containsExactly(
                "User:debugger * TOPIC:* READ ALLOW"
        )
        assertThat(resolver.findTopicAffectingAclRules("audit-errors")).containsExactly(
                "User:debugger * TOPIC:* READ ALLOW"
        )

        //check which rules affects each consumer group
        assertThat(resolver.findGroupAffectingAclRules("auth")).containsExactly("User:security 10.0.0.1 GROUP:auth ALL ALLOW")
        assertThat(resolver.findGroupAffectingAclRules("billing")).containsExactly("User:charging * GROUP:billing ALL ALLOW")
        assertThat(resolver.findGroupAffectingAclRules("billing-report")).containsExactly("User:finance-stats * GROUP:billing-report ALL ALLOW")
        assertThat(resolver.findGroupAffectingAclRules("logging")).containsExactly("User:debugger * GROUP:logging ALL ALLOW")

        //check which topics each rule affects
        assertThat(resolver.findAffectedTopics("User:security 10.0.0.1 TOPIC:logins READ ALLOW")).containsExactly("logins")
        assertThat(resolver.findAffectedTopics("User:security 10.0.0.1 GROUP:auth ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedTopics("User:charging * TOPIC:payment* ALL ALLOW")).containsExactly("payment-books", "payment-movies")
        assertThat(resolver.findAffectedTopics("User:charging * GROUP:billing ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedTopics("User:finance-stats * TOPIC:payment* READ ALLOW")).containsExactly("payment-books", "payment-movies")
        assertThat(resolver.findAffectedTopics("User:finance-stats * GROUP:billing-report ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedTopics("User:debugger * TOPIC:* READ ALLOW")).containsExactly("logins", "payment-books", "payment-movies", "audit-views", "audit-errors")
        assertThat(resolver.findAffectedTopics("User:debugger * TOPIC:logins ALL DENY")).containsExactly("logins")
        assertThat(resolver.findAffectedTopics("User:debugger * GROUP:logging ALL ALLOW")).isEmpty()

        //check which groups each rule affects
        assertThat(resolver.findAffectedGroups("User:security 10.0.0.1 TOPIC:logins READ ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("User:security 10.0.0.1 GROUP:auth ALL ALLOW")).containsExactly("auth")
        assertThat(resolver.findAffectedGroups("User:charging * TOPIC:payment* ALL ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("User:charging * GROUP:billing ALL ALLOW")).containsExactly("billing")
        assertThat(resolver.findAffectedGroups("User:finance-stats * TOPIC:payment* READ ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("User:finance-stats * GROUP:billing-report ALL ALLOW")).containsExactly("billing-report")
        assertThat(resolver.findAffectedGroups("User:debugger * TOPIC:* READ ALLOW")).isEmpty()
        assertThat(resolver.findAffectedGroups("User:debugger * TOPIC:logins ALL DENY")).isEmpty()
        assertThat(resolver.findAffectedGroups("User:debugger * GROUP:logging ALL ALLOW")).containsExactly("logging")

        //check which principals are affected bu quota entities
        assertThat(resolver.findAffectedPrincipals("debugger|<all>")).containsExactly("User:debugger")
        assertThat(resolver.findAffectedPrincipals("debugger|<default>")).containsExactly("User:debugger")
        assertThat(resolver.findAffectedPrincipals("debugger|client-1")).containsExactly("User:debugger")
        assertThat(resolver.findAffectedPrincipals("unrelated|<all>")).isEmpty()
        assertThat(resolver.findAffectedPrincipals("<default>|<all>")).containsExactly("User:security", "User:charging", "User:finance-stats")
        assertThat(resolver.findAffectedPrincipals("<all>|some-client")).isEmpty()
        assertThat(resolver.findAffectedPrincipals("third|<all>")).isEmpty()

        //check which entities are affecting principal
        assertThat(resolver.findAffectingQuotaEntities("User:security")).containsExactly("<default>|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:charging")).containsExactly("<default>|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:finance-stats")).containsExactly("<default>|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:debugger")).containsExactly("debugger|<all>")
        assertThat(resolver.findAffectingQuotaEntities("User:new")).containsExactly("<default>|<all>")
    }

    private val cluster = "kfk-test"

    private fun newResolverWithClusterData(
        topics: List<TopicName> = emptyList(),
        groups: List<ConsumerGroupId> = emptyList(),
        quotaEntities: List<QuotaEntity> = emptyList(),
        acls: List<String> = emptyList()
    ): AclLinkResolver {
        val data = AclClusterLinkData(topics, groups, quotaEntities, acls.map { it.parseAcl() })
        val provider: AclResolverDataProvider = mock()
        whenever(provider.getClustersData()).thenReturn(mapOf(cluster to data))
        return AclLinkResolver(provider)
    }

    private fun AclLinkResolver.findAffectedTopics(acl: String) =
            findAffectedTopics(acl.parseAcl(), cluster)

    private fun AclLinkResolver.findAffectedGroups(acl: String) =
            findAffectedConsumerGroups(acl.parseAcl(), cluster)

    private fun AclLinkResolver.findAffectingQuotaEntities(principal: PrincipalId)  =
            findPrincipalAffectingQuotas(principal, cluster).map { it.asID() }

    private fun AclLinkResolver.findTopicAffectingAclRules(topic: TopicName) =
            findTopicAffectingAclRules(topic, cluster).map { it.asString() }

    private fun AclLinkResolver.findGroupAffectingAclRules(group: ConsumerGroupId) =
            findConsumerGroupAffectingAclRules(group, cluster).map { it.asString() }

    private fun AclLinkResolver.findAffectedPrincipals(quotaEntityID: QuotaEntityID) = findAffectedPrincipals(
        QuotaEntity.fromID(quotaEntityID))
    private fun AclLinkResolver.findAffectedPrincipals(entity: QuotaEntity) =
            findQuotaAffectingPrincipals(entity, cluster)

}