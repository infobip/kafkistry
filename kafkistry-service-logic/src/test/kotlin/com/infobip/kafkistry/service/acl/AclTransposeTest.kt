package com.infobip.kafkistry.service.acl

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.model.Presence
import com.infobip.kafkistry.model.PresenceType
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.service.NamedTypeQuantity
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.acl.AvailableAclOperation.*
import org.junit.Test

@Suppress("LocalVariableName")
class AclTransposeTest {

    @Test
    fun testTranspose() {
        val rule1 = "P * TOPIC:t ALL ALLOW".parseAcl()
        val rule2 = "P * GROUP:t ALL ALLOW".parseAcl()
        val rule3 = "P 192.168.1.1 TOPIC:* ALL DENY".parseAcl()

        val rule1_c1 = rule1.withStatus(OK)
        val rule2_c1 = rule2.withStatus(OK)

        val rule1_c2 = rule1.withStatus(OK)
        val rule2_c2 = rule2.withStatus(MISSING)
        val rule3_c2 = rule3.withStatus(UNKNOWN)

        val rule1_c3 = rule1.withStatus(NOT_PRESENT_AS_EXPECTED)
        val rule2_c3 = rule2.withStatus(OK)

        val pAcls = PrincipalAclRules(
                principal = "P",
                description = "for testing",
                owner = "transposer",
                rules = listOf(
                        rule1.toAclRule(Presence(PresenceType.INCLUDED_CLUSTERS, listOf("c_1", "c_2"))),
                        rule2.toAclRule(Presence(PresenceType.ALL_CLUSTERS))
                )
        )

        val rulesOnCluster1 = PrincipalAclsClusterInspection(
                principal = "P",
                clusterIdentifier = "c_1", clusterTags = emptyList(),
                status = AclStatus(true, listOf(OK has 2)),
                statuses = listOf(rule1_c1, rule2_c1),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val rulesOnCluster2 = PrincipalAclsClusterInspection(
                principal = "P",
                clusterIdentifier = "c_2", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, MISSING has 1, UNKNOWN has 1)),
                statuses = listOf(rule1_c2, rule2_c2, rule3_c2),
                availableOperations = listOf(CREATE_MISSING_ACLS, DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyList(),
        )
        val rulesOnCluster3 = PrincipalAclsClusterInspection(
                principal = "P",
                clusterIdentifier = "c_3", clusterTags = emptyList(),
                status = AclStatus(true, listOf(OK has 1, NOT_PRESENT_AS_EXPECTED has 1)),
                statuses = listOf(rule1_c3, rule2_c3),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )

        val rulesPerCluster = PrincipalAclsInspection(
                principal = "P",
                principalAcls = pAcls,
                clusterInspections = listOf(rulesOnCluster1, rulesOnCluster2, rulesOnCluster3),
                status = AclStatus(false, listOf(OK has 4, MISSING has 1, UNKNOWN has 1, NOT_PRESENT_AS_EXPECTED has 1)),
                availableOperations = listOf(CREATE_MISSING_ACLS, DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyMap(),
        )
        val clustersPerRule = rulesPerCluster.transpose()

        assertThat(clustersPerRule.statuses).hasSize(3)
        assertThat(clustersPerRule.principalAcls).isEqualTo(pAcls)
        assertThat(clustersPerRule.statuses[0]).isEqualTo(
            AclRuleClustersInspection(
                aclRule = rule1,
                status = AclStatus(true, listOf(OK has 2, NOT_PRESENT_AS_EXPECTED has 1)),
                clusterStatuses = mapOf(
                        "c_1" to rule1_c1,
                        "c_2" to rule1_c2,
                        "c_3" to rule1_c3
                ),
                availableOperations = emptyList()
        )
        )
        assertThat(clustersPerRule.statuses[1]).isEqualTo(
            AclRuleClustersInspection(
                aclRule = rule2,
                status = AclStatus(false, listOf(OK has 2, MISSING has 1)),
                clusterStatuses = mapOf(
                        "c_1" to rule2_c1,
                        "c_2" to rule2_c2,
                        "c_3" to rule2_c3
                ),
                availableOperations = listOf(CREATE_MISSING_ACLS, EDIT_PRINCIPAL_ACLS)
        )
        )
        assertThat(clustersPerRule.statuses[2]).isEqualTo(
            AclRuleClustersInspection(
                aclRule = rule3,
                status = AclStatus(false, listOf(UNKNOWN has 1)),
                clusterStatuses = mapOf(
                        "c_2" to rule3_c2
                ),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS)
        )
        )
        assertThat(clustersPerRule.status).isEqualTo(
                AclStatus(false, listOf(OK has 4, NOT_PRESENT_AS_EXPECTED has 1, MISSING has 1, UNKNOWN has 1))
        )

    }

    private fun KafkaAclRule.withStatus(statusType: AclInspectionResultType) = AclRuleStatus(
        listOf(statusType), this, emptyList(), emptyList(), statusType.availableOperations(true), emptyList(),
    )
    private infix fun AclInspectionResultType.has(count: Int) = NamedTypeQuantity(this, count) 
}