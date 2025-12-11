package com.infobip.kafkistry.service.it

import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.kafka.parseAcl
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.PresenceType.EXCLUDED_CLUSTERS
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.acl.*
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.CONFLICT
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.DETACHED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.SECURITY_DISABLED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.acl.AclInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.acl.AvailableAclOperation.*
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Suppress("PrivatePropertyName", "LocalVariableName")
@SpringBootTest
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("it", "dir")
class AclsInspectionTest {

    @Autowired
    private lateinit var inspection: AclsInspectionService

    @Autowired
    private lateinit var suggestion: AclsSuggestionService

    @Autowired
    private lateinit var aclLinkResolver: AclLinkResolver

    @MockitoBean
    private lateinit var stateProvider: KafkaClustersStateProvider

    @MockitoBean
    private lateinit var groupsStateProvider: KafkaConsumerGroupsProvider

    @Autowired
    private lateinit var acls: AclsRegistryService

    @Autowired
    private lateinit var clusters: ClustersRegistryService

    @Autowired
    private lateinit var topics: TopicsRegistryService

    @BeforeEach
    fun before() {
        Mockito.reset(stateProvider, groupsStateProvider)
        topics.deleteAll(UpdateContext("test msg"))
        clusters.removeAll()
        acls.deleteAll(UpdateContext("test msg"))
        aclLinkResolver.invalidateCache()
    }

    @AfterEach
    fun after() {
        clusters.removeAll()
    }

    private val presenceAll = Presence(PresenceType.ALL_CLUSTERS)
    private fun presence(vararg clusters: KafkaClusterIdentifier) = Presence(PresenceType.INCLUDED_CLUSTERS, clusters.toList())

    private val cluster1 = newCluster("c_1")
    private val rule_X_T1 = "User:X * TOPIC:t1 ALL ALLOW".parseAcl()

    private fun AclsRegistryService.create(principalAcls: PrincipalAclRules) = create(principalAcls, UpdateContext("test msg"))

    @Test
    fun `test non-existing principal on empty`() {
        val result = inspection.inspectPrincipalAcls("non-existing")
        result.status.assertOk()
        assertThat(result.status.statusCounts).isEmpty()
    }

    @Test
    fun `test ok rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(rule_X_T1, topics = listOf("t1"))
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(OK)
    }

    @Test
    fun `test detached rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(rule_X_T1)
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertNotOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(DETACHED)
    }

    @Test
    fun `test missing rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules()
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertNotOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(MISSING)
    }

    @Test
    fun `test unexpected rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules(presence()).first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(rule_X_T1, topics = listOf("t1"))
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertNotOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(UNEXPECTED)
    }

    @Test
    fun `test unknown rule`() {
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(rule_X_T1, topics = listOf("t1"))
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertNotOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(UNKNOWN)
    }

    @Test
    fun `test missing_as_expected rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules(presence()).first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules()
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(NOT_PRESENT_AS_EXPECTED)
    }

    @Test
    fun `test security_disabled rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(security = false)
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(SECURITY_DISABLED)
    }

    @Test
    fun `test not-visible-cluster rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(stateType = StateType.UNREACHABLE)
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertNotOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(CLUSTER_UNREACHABLE)
    }

    @Test
    fun `test disabled-cluster rule`() {
        acls.create(listOf(rule_X_T1).toPrincipalAclRules().first())
        clusters.addCluster(cluster1)
        cluster1.mockClusterStateRules(stateType = StateType.DISABLED)
        val result = inspection.inspectPrincipalAclsOnCluster("User:X", cluster1.identifier)
        result.status.assertOk()
        assertThat(result.statuses.flatMap { it.statusTypes }).containsExactly(CLUSTER_DISABLED)
    }

    //1.5 hours to write this test correctly
    @Test
    fun `test complex setup`() {
        val c1 = newCluster(identifier = "c_1", clusterId = "1")
        val c2 = newCluster(identifier = "c_2", clusterId = "2")
        val c3 = newCluster(identifier = "c_3", clusterId = "3")
        clusters.addCluster(c1)
        clusters.addCluster(c2)
        clusters.addCluster(c3)

        val rule_p1_r1 = "User:P1 * TOPIC:t1 READ ALLOW".parseAcl()
        val rule_p1_r2 = "User:P1 * GROUP:g1 ALL ALLOW".parseAcl()
        val p1Rules = PrincipalAclRules(
                principal = "User:P1",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        rule_p1_r1.toAclRule(presenceAll),
                        rule_p1_r2.toAclRule(presenceAll)
                )
        )

        val rule_p2_r1 = "User:P2 * TOPIC:t1 WRITE ALLOW".parseAcl()
        val p2Rules = PrincipalAclRules(
                principal = "User:P2",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        rule_p2_r1.toAclRule(presenceAll)
                )
        )

        val rule_p3_r1 = "User:P3 * TOPIC:t1 READ ALLOW".parseAcl()
        val rule_p3_r2 = "User:P3 * GROUP:g1 ALL ALLOW".parseAcl()
        val rule_p3_r3 = "User:P3 * TOPIC:s* ALL DENY".parseAcl()
        val p3Rules = PrincipalAclRules(
                principal = "User:P3",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        rule_p3_r1.toAclRule(presence("c_1", "c_2")),
                        rule_p3_r2.toAclRule(presence("c_2", "c_3")),
                        rule_p3_r3.toAclRule(presenceAll)
                )
        )

        val rule_p2_r2 = "User:P2 * TOPIC:t2 CREATE ALLOW".parseAcl()
        val rule_p4_r1 = "User:P4 * TOPIC:t1 READ ALLOW".parseAcl()
        val rule_p4_r2 = "User:P4 * GROUP:g1 ALL ALLOW".parseAcl()

        acls.create(p1Rules)
        acls.create(p2Rules)
        acls.create(p3Rules)

        c1.mockClusterStateRules(
            rule_p1_r1, rule_p1_r2,
            rule_p2_r1, rule_p2_r2,
            rule_p3_r1, rule_p3_r2, rule_p3_r3,
            rule_p4_r1, rule_p4_r2,
            topics = listOf("t1", "t2"), groups = listOf("g1"),
        )
        c2.mockClusterStateRules(
            rule_p1_r1, rule_p1_r2,
            rule_p2_r1,
            rule_p3_r1, rule_p3_r2, rule_p3_r3,
            rule_p4_r1, rule_p4_r2,
            topics = listOf("t1"), groups = listOf("g1"),
        )
        c3.mockClusterStateRules(
            rule_p1_r1, rule_p1_r2,
            rule_p2_r1,
            rule_p3_r2, rule_p3_r3,
            topics = listOf("t1"), groups = listOf("g1"),
        )

        //do actual inspections
        val clustersResult = inspection.inspectAllClusters()
        val principalsResult = inspection.inspectAllPrincipals()
        val unknownPrincipals = inspection.inspectUnknownPrincipals()

        //instantiate expected response model
        val expected_P1_on_c1 = PrincipalAclsClusterInspection(
                principal = "User:P1",
                clusterIdentifier = "c_1", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, CONFLICT has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p1_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(CONFLICT), rule_p1_r2, listOf(), listOf("g1"), listOf(), listOf(rule_p3_r2, rule_p4_r2))
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P2_on_c1 = PrincipalAclsClusterInspection(
                principal = "User:P2",
                clusterIdentifier = "c_1", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, UNKNOWN has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p2_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(UNKNOWN), rule_p2_r2, listOf("t2"), listOf(), listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS), listOf())
                ),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P3_on_c1 = PrincipalAclsClusterInspection(
                principal = "User:P3",
                clusterIdentifier = "c_1", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, UNEXPECTED has 1, CONFLICT has 1, DETACHED has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p3_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(UNEXPECTED, CONFLICT), rule_p3_r2, listOf(), listOf("g1"), listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS), listOf(rule_p1_r2, rule_p4_r2)),
                        AclRuleStatus(listOf(DETACHED), rule_p3_r3, listOf(), listOf(), listOf(), listOf())
                ),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P4_on_c1 = PrincipalAclsClusterInspection(
                principal = "User:P4",
                clusterIdentifier = "c_1", clusterTags = emptyList(),
                status = AclStatus(false, listOf(UNKNOWN has 2, CONFLICT has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(UNKNOWN), rule_p4_r1, listOf("t1"), listOf(), listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL), listOf()),
                        AclRuleStatus(listOf(UNKNOWN, CONFLICT), rule_p4_r2, listOf(), listOf("g1"), listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL), listOf(rule_p1_r2, rule_p3_r2))
                ),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL),
                affectingQuotaEntities = emptyList(),
        )

        val expected_P1_on_c2 = PrincipalAclsClusterInspection(
                principal = "User:P1",
                clusterIdentifier = "c_2", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, CONFLICT has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p1_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(CONFLICT), rule_p1_r2, listOf(), listOf("g1"), listOf(), listOf(rule_p3_r2, rule_p4_r2))
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P2_on_c2 = PrincipalAclsClusterInspection(
                principal = "User:P2",
                clusterIdentifier = "c_2", clusterTags = emptyList(),
                status = AclStatus(true, listOf(OK has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p2_r1, listOf("t1"), listOf(), listOf(), listOf())
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P3_on_c2 = PrincipalAclsClusterInspection(
                principal = "User:P3",
                clusterIdentifier = "c_2", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, CONFLICT has 1, DETACHED has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p3_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(CONFLICT), rule_p3_r2, listOf(), listOf("g1"), listOf(), listOf(rule_p1_r2, rule_p4_r2)),
                        AclRuleStatus(listOf(DETACHED), rule_p3_r3, listOf(), listOf(), listOf(), listOf())
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P4_on_c2 = PrincipalAclsClusterInspection(
                principal = "User:P4",
                clusterIdentifier = "c_2", clusterTags = emptyList(),
                status = AclStatus(false, listOf(UNKNOWN has 2, CONFLICT has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(UNKNOWN), rule_p4_r1, listOf("t1"), listOf(), listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL), listOf()),
                        AclRuleStatus(listOf(UNKNOWN, CONFLICT), rule_p4_r2, listOf(), listOf("g1"), listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL), listOf(rule_p1_r2, rule_p3_r2))
                ),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL),
                affectingQuotaEntities = emptyList(),
        )

        val expected_P1_on_c3 = PrincipalAclsClusterInspection(
                principal = "User:P1",
                clusterIdentifier = "c_3", clusterTags = emptyList(),
                status = AclStatus(false, listOf(OK has 1, CONFLICT has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p1_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(CONFLICT), rule_p1_r2, listOf(), listOf("g1"), listOf(), listOf(rule_p3_r2))
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P2_on_c3 = PrincipalAclsClusterInspection(
                principal = "User:P2",
                clusterIdentifier = "c_3", clusterTags = emptyList(),
                status = AclStatus(true, listOf(OK has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(OK), rule_p2_r1, listOf("t1"), listOf(), listOf(), listOf())
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P3_on_c3 = PrincipalAclsClusterInspection(
                principal = "User:P3",
                clusterIdentifier = "c_3", clusterTags = emptyList(),
                status = AclStatus(false, listOf(NOT_PRESENT_AS_EXPECTED has 1, CONFLICT has 1, DETACHED has 1)),
                statuses = listOf(
                        AclRuleStatus(listOf(NOT_PRESENT_AS_EXPECTED), rule_p3_r1, listOf("t1"), listOf(), listOf(), listOf()),
                        AclRuleStatus(listOf(CONFLICT), rule_p3_r2, listOf(), listOf("g1"), listOf(), listOf(rule_p1_r2)),
                        AclRuleStatus(listOf(DETACHED), rule_p3_r3, listOf(), listOf(), listOf(), listOf())
                ),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )
        val expected_P4_on_c3 = PrincipalAclsClusterInspection(
                principal = "User:P4",
                clusterIdentifier = "c_3", clusterTags = emptyList(),
                status = AclStatus(true, listOf()),
                statuses = listOf(),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyList(),
        )


        //do the assertions

        assertThat(clustersResult).hasSize(3)

        assertThat(clustersResult[0].clusterIdentifier).isEqualTo("c_1")
        assertThat(clustersResult[0].principalAclsInspections).hasSize(4)
        assertThat(clustersResult[0].principalAclsInspections[0]).isEqualTo(expected_P1_on_c1)
        assertThat(clustersResult[0].principalAclsInspections[1]).isEqualTo(expected_P2_on_c1)
        assertThat(clustersResult[0].principalAclsInspections[2]).isEqualTo(expected_P3_on_c1)
        assertThat(clustersResult[0].principalAclsInspections[3]).isEqualTo(expected_P4_on_c1)
        assertThat(clustersResult[0].status).isEqualTo(AclStatus(false, listOf(OK has 3, CONFLICT has 3, UNKNOWN has 3, UNEXPECTED has 1, DETACHED has 1)))

        assertThat(clustersResult[1].clusterIdentifier).isEqualTo("c_2")
        assertThat(clustersResult[1].principalAclsInspections).hasSize(4)
        assertThat(clustersResult[1].principalAclsInspections[0]).isEqualTo(expected_P1_on_c2)
        assertThat(clustersResult[1].principalAclsInspections[1]).isEqualTo(expected_P2_on_c2)
        assertThat(clustersResult[1].principalAclsInspections[2]).isEqualTo(expected_P3_on_c2)
        assertThat(clustersResult[1].principalAclsInspections[3]).isEqualTo(expected_P4_on_c2)
        assertThat(clustersResult[1].status).isEqualTo(AclStatus(false, listOf(OK has 3, CONFLICT has 3, UNKNOWN has 2, DETACHED has 1)))

        assertThat(clustersResult[2].clusterIdentifier).isEqualTo("c_3")
        assertThat(clustersResult[2].principalAclsInspections).hasSize(3)
        assertThat(clustersResult[2].principalAclsInspections[0]).isEqualTo(expected_P1_on_c3)
        assertThat(clustersResult[2].principalAclsInspections[1]).isEqualTo(expected_P2_on_c3)
        assertThat(clustersResult[2].principalAclsInspections[2]).isEqualTo(expected_P3_on_c3)
        assertThat(clustersResult[2].status).isEqualTo(AclStatus(false, listOf(OK has 2, CONFLICT has 2, NOT_PRESENT_AS_EXPECTED has 1, DETACHED has 1)))

        assertThat(principalsResult).hasSize(3)
        assertThat(principalsResult[0]).isEqualTo(
            PrincipalAclsInspection(
                principal = "User:P1",
                principalAcls = p1Rules,
                clusterInspections = listOf(expected_P1_on_c1, expected_P1_on_c2, expected_P1_on_c3),
                status = AclStatus(false, listOf(OK has 3, CONFLICT has 3)),
                availableOperations = emptyList(),
                affectingQuotaEntities = emptyMap(),
        )
        )
        assertThat(principalsResult[1]).isEqualTo(
            PrincipalAclsInspection(
                principal = "User:P2",
                principalAcls = p2Rules,
                clusterInspections = listOf(expected_P2_on_c1, expected_P2_on_c2, expected_P2_on_c3),
                status = AclStatus(false, listOf(OK has 3, UNKNOWN has 1)),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyMap(),
        )
        )
        assertThat(principalsResult[2]).isEqualTo(
            PrincipalAclsInspection(
                principal = "User:P3",
                principalAcls = p3Rules,
                clusterInspections = listOf(expected_P3_on_c1, expected_P3_on_c2, expected_P3_on_c3),
                status = AclStatus(false, listOf(CONFLICT has 3, DETACHED has 3, OK has 2, UNEXPECTED has 1, NOT_PRESENT_AS_EXPECTED has 1)),
                availableOperations = listOf(DELETE_UNWANTED_ACLS, EDIT_PRINCIPAL_ACLS),
                affectingQuotaEntities = emptyMap(),
        )
        )

        assertThat(unknownPrincipals).isEqualTo(listOf(
                PrincipalAclsInspection(
                        principal = "User:P4",
                        principalAcls = null,
                        clusterInspections = listOf(expected_P4_on_c1, expected_P4_on_c2, expected_P4_on_c3),
                        status = AclStatus(false, listOf(UNKNOWN has 4, CONFLICT has 2)),
                        availableOperations = listOf(DELETE_UNWANTED_ACLS, IMPORT_PRINCIPAL),
                        affectingQuotaEntities = emptyMap(),
                )
        ))

        //test the suggestion operations

        //P1
        assertThrows<KafkistryIllegalStateException> {
            suggestion.suggestPrincipalAclsImport("User:P1")
        }
        //ignore result, no automatic conflict rsulotion suggestions
        suggestion.suggestPrincipalAclsUpdate("User:P1")

        //P2
        assertThrows<KafkistryIllegalStateException> {
            suggestion.suggestPrincipalAclsImport("User:P2")
        }
        val suggested_P2_acls = suggestion.suggestPrincipalAclsUpdate("User:P2")
        assertThat(suggested_P2_acls).isEqualTo(
            PrincipalAclRules(
                principal = "User:P2",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        rule_p2_r1.toAclRule(presenceAll),
                        rule_p2_r2.toAclRule(presence("c_1"))
                )
            )
        )

        //P3
        assertThrows<KafkistryIllegalStateException> {
            suggestion.suggestPrincipalAclsImport("User:P3")
        }
        val suggested_P3_acls = suggestion.suggestPrincipalAclsUpdate("User:P3")
        assertThat(suggested_P3_acls).isEqualTo(
            PrincipalAclRules(
                principal = "User:P3",
                description = "for testing",
                owner = "Team_Test",
                rules = listOf(
                        rule_p3_r1.toAclRule(Presence(EXCLUDED_CLUSTERS, listOf("c_3"))),
                        rule_p3_r2.toAclRule(presenceAll),
                        rule_p3_r3.toAclRule(presenceAll)
                )
            )
        )

        //P4
        val suggested_P4_acls = suggestion.suggestPrincipalAclsImport("User:P4")
        assertThat(suggested_P4_acls).isEqualTo(
            PrincipalAclRules(
                principal = "User:P4",
                description = "",
                owner = "",
                rules = listOf(
                        rule_p4_r1.toAclRule(Presence(EXCLUDED_CLUSTERS, listOf("c_3"))),
                        rule_p4_r2.toAclRule(Presence(EXCLUDED_CLUSTERS, listOf("c_3")))
                )
            )
        )
        assertThrows<KafkistryIllegalStateException> {
            suggestion.suggestPrincipalAclsUpdate("User:P4")
        }

    }

    private fun KafkaCluster.mockClusterStateRules(
            vararg rules: KafkaAclRule,
            security: Boolean = true,
            stateType: StateType = StateType.VISIBLE,
            topics: List<TopicName> = emptyList(),
            groups: List<ConsumerGroupId> = emptyList(),
    ) {
        whenever(stateProvider.getLatestClusterState(identifier)).thenReturn(newState(
                acls = rules.toList(),
                securityEnabled = security,
                stateType = stateType,
                topics = topics.map { newTopic(name = it) }.toTypedArray(),
        ))
        whenever(groupsStateProvider.getLatestState(identifier)).thenReturn(StateData(
            stateType = stateType,
            clusterIdentifier = identifier,
            stateTypeName = "groups",
            lastRefreshTime = System.currentTimeMillis(),
            value = if (stateType == StateType.VISIBLE) {
                ClusterConsumerGroups(
                    consumerGroups = groups.associateWith { Maybe.Absent(RuntimeException()) },
                )
            } else null,
        ))
    }

    private fun List<KafkaAclRule>.toPrincipalAclRules(
            presence: Presence = presenceAll
    ): List<PrincipalAclRules> {
        return groupBy { it.principal }
                .map { (principal, rules) ->
                    PrincipalAclRules(
                            principal = principal,
                            description = "for testing",
                            owner = "Team_Test",
                            rules = rules.map { it.toAclRule(presence) })
                }
    }

    private fun AclStatus.assertOk() {
        assertThat(ok).`as`("$this").isEqualTo(true)
    }

    private fun AclStatus.assertNotOk() {
        assertThat(ok).`as`("$this").isEqualTo(false)
    }

    private infix fun AclInspectionResultType.has(count: Int) = NamedTypeQuantity(this, count)

}