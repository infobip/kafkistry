package com.infobip.kafkistry.service.it

import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.infobip.kafkistry.kafka.ClientQuota
import com.infobip.kafkistry.kafkastate.ClusterQuotas
import com.infobip.kafkistry.kafkastate.KafkaQuotasProvider
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.PresenceType.EXCLUDED_CLUSTERS
import com.infobip.kafkistry.model.PresenceType.INCLUDED_CLUSTERS
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.quotas.*
import com.infobip.kafkistry.service.quotas.AvailableQuotasOperation.*
import com.infobip.kafkistry.TestDirsPathInitializer
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_DISABLED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.CLUSTER_UNREACHABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.MISSING
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.NOT_PRESENT_AS_EXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.OK
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNAVAILABLE
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNEXPECTED
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.UNKNOWN
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType.Companion.WRONG_VALUE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Suppress("LocalVariableName")
@SpringBootTest
@ContextConfiguration(initializers = [TestDirsPathInitializer::class])
@ActiveProfiles("it", "dir")
class QuotasInspectionTest {

    @Autowired
    private lateinit var inspection: QuotasInspectionService

    @Autowired
    private lateinit var suggestion: QuotasSuggestionService

    @MockitoBean
    private lateinit var stateProvider: KafkaQuotasProvider

    @Autowired
    private lateinit var quotas: QuotasRegistryService

    @Autowired
    private lateinit var clusters: ClustersRegistryService

    @BeforeEach
    fun before() {
        Mockito.reset(stateProvider)
        clusters.removeAll()
        quotas.deleteAll(UpdateContext("test msg"))
    }

    @AfterEach
    fun after() {
        clusters.removeAll()
    }

    private val cluster1 = newCluster("c_1")
    private val quota1 = newQuota(
        entity = QuotaEntity.user("test-entity"),
        properties = QuotaProperties(20_000, 40_000, 85.0)
    )

    private fun QuotasRegistryService.create(quotaDescription: QuotaDescription) =
        create(quotaDescription, UpdateContext("test msg"))

    @Test
    fun `test non-existing entity on empty`() {
        val result = inspection.inspectEntityOnClusters("non-existing|<any>")
        assertThat(result.status).isEqualTo(QuotaStatus.EMPTY_OK)
    }

    @Test
    fun `test ok quotas`() {
        quotas.create(quota1)
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas(ClientQuota(quota1.entity, quota1.properties))
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(OK)
    }

    @Test
    fun `test missing quotas`() {
        quotas.create(quota1)
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas()
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(MISSING)
    }

    @Test
    fun `test unknown quotas`() {
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas(ClientQuota(quota1.entity, quota1.properties))
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(UNKNOWN)
    }

    @Test
    fun `test unexpected quotas`() {
        quotas.create(quota1.copy(presence = Presence(EXCLUDED_CLUSTERS, listOf(cluster1.identifier))))
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas(ClientQuota(quota1.entity, quota1.properties))
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(UNEXPECTED)
    }

    @Test
    fun `test missing_as_expected quotas`() {
        quotas.create(quota1.copy(presence = Presence(EXCLUDED_CLUSTERS, listOf(cluster1.identifier))))
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas()
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(NOT_PRESENT_AS_EXPECTED)
    }

    @Test
    fun `test not-visible-cluster quotas`() {
        quotas.create(quota1)
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas(stateType = StateType.UNREACHABLE)
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(CLUSTER_UNREACHABLE)
    }

    @Test
    fun `test disabled-cluster quotas`() {
        quotas.create(quota1)
        clusters.addCluster(cluster1)
        cluster1.mockClusterQuotas(stateType = StateType.DISABLED)
        val result = inspection.inspectEntityQuotasOnCluster(quota1.entity.asID(), cluster1.identifier)
        assertThat(result.statusType).isEqualTo(CLUSTER_DISABLED)
    }

    @Test
    fun `test complex setup`() {
        val c1 = newCluster(identifier = "c_1", clusterId = "1")
        val c2 = newCluster(identifier = "c_2", clusterId = "2")
        val c3 = newCluster(identifier = "c_3", clusterId = "3")
        clusters.addCluster(c1)
        clusters.addCluster(c2)
        clusters.addCluster(c3)

        val e1_q = newQuota(
            entity = QuotaEntity("u1"),
            presence = Presence(EXCLUDED_CLUSTERS, listOf("c_2")),
            properties = QuotaProperties(1_001, 2_001, 101.0),
            clusterOverrides = mapOf(
                "c_1" to QuotaProperties(10_001, 20_001, 1001.0),
            )
        )
        val e2_q = newQuota(
            entity = QuotaEntity("u2"),
            presence = Presence(INCLUDED_CLUSTERS, listOf("c_1", "c_2")),
            properties = QuotaProperties(1_002, 2_002, 102.0),
            clusterOverrides = mapOf(
                "c_1" to QuotaProperties(null, 20_002, null),
            )
        )

        val c1_e1 = ClientQuota(QuotaEntity("u1"), QuotaProperties(10_001, 20_001, 1001.0))
        val c1_e2 = ClientQuota(QuotaEntity("u2"), QuotaProperties(null, 2_002, 102.0))
        val c1_e3 = ClientQuota(QuotaEntity("u3"), QuotaProperties(1_003, null, null))
        //val c2_e1 = ClientQuota(QuotaEntity("u1"), QuotaProperties(1_001, 2_001, 101.0))
        //val c2_e2 = ClientQuota(QuotaEntity("u2"), QuotaProperties(null, 20_002, null))
        val c2_e3 = ClientQuota(QuotaEntity("u3"), QuotaProperties(1_003, null, null))
        val c3_e1 = ClientQuota(QuotaEntity("u1"), QuotaProperties(1_001, 2_001, 101.0))
        val c3_e2 = ClientQuota(QuotaEntity("u2"), QuotaProperties(1_002, 2_002, 102.0))
        //val c3_e3 = ClientQuota(QuotaEntity("u3"), QuotaProperties(10_003, null, null))

        quotas.create(e1_q)
        quotas.create(e2_q)

        c1.mockClusterQuotas(c1_e1, c1_e2, c1_e3)
        c2.mockClusterQuotas(/*c2_e1, c2_e2,*/c2_e3)
        c3.mockClusterQuotas(c3_e1, c3_e2/*, c3_e3*/)

        val r_c1_e1 = QuotasInspection(
            entity = QuotaEntity.user("u1"),
            clusterIdentifier = "c_1",
            valuesInspection = ValuesInspection(
                ok = true, producerByteRateOk = true, consumerByteRateOk = true, requestPercentageOk = true
            ),
            statusType = OK,
            expectedQuota = QuotaProperties(10_001, 20_001, 1001.0),
            actualQuota = QuotaProperties(10_001, 20_001, 1001.0),
            affectedPrincipals = emptyList(),
            availableOperations = emptyList(),
        )
        val r_c2_e1 = QuotasInspection(
            entity = QuotaEntity.user("u1"),
            clusterIdentifier = "c_2",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = NOT_PRESENT_AS_EXPECTED,
            expectedQuota = null,
            actualQuota = null,
            affectedPrincipals = emptyList(),
            availableOperations = emptyList(),
        )
        val r_c3_e1 = QuotasInspection(
            entity = QuotaEntity.user("u1"),
            clusterIdentifier = "c_3",
            valuesInspection = ValuesInspection(
                ok = true, producerByteRateOk = true, consumerByteRateOk = true, requestPercentageOk = true
            ),
            statusType = OK,
            expectedQuota = QuotaProperties(1_001, 2_001, 101.0),
            actualQuota = QuotaProperties(1_001, 2_001, 101.0),
            affectedPrincipals = emptyList(),
            availableOperations = emptyList(),
        )

        val r_c1_e2 = QuotasInspection(
            entity = QuotaEntity.user("u2"),
            clusterIdentifier = "c_1",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = true, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = WRONG_VALUE,
            expectedQuota = QuotaProperties(null, 20_002, null),
            actualQuota = QuotaProperties(null, 2_002, 102.0),
            affectedPrincipals = emptyList(),
            availableOperations = listOf(EDIT_CLIENT_QUOTAS, ALTER_WRONG_QUOTAS),
        )
        val r_c2_e2 = QuotasInspection(
            entity = QuotaEntity.user("u2"),
            clusterIdentifier = "c_2",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = MISSING,
            expectedQuota = QuotaProperties(1_002, 2_002, 102.0),
            actualQuota = null,
            affectedPrincipals = emptyList(),
            availableOperations = listOf(CREATE_MISSING_QUOTAS, EDIT_CLIENT_QUOTAS),
        )
        val r_c3_e2 = QuotasInspection(
            entity = QuotaEntity.user("u2"),
            clusterIdentifier = "c_3",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = UNEXPECTED,
            expectedQuota = null,
            actualQuota = QuotaProperties(1_002, 2_002, 102.0),
            affectedPrincipals = emptyList(),
            availableOperations = listOf(DELETE_UNWANTED_QUOTAS, EDIT_CLIENT_QUOTAS),
        )

        val r_c1_e3 = QuotasInspection(
            entity = QuotaEntity.user("u3"),
            clusterIdentifier = "c_1",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = UNKNOWN,
            expectedQuota = null,
            actualQuota = QuotaProperties(1_003, null, null),
            affectedPrincipals = emptyList(),
            availableOperations = listOf(DELETE_UNWANTED_QUOTAS, IMPORT_CLIENT_QUOTAS),
        )
        val r_c2_e3 = QuotasInspection(
            entity = QuotaEntity.user("u3"),
            clusterIdentifier = "c_2",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = UNKNOWN,
            expectedQuota = null,
            actualQuota = QuotaProperties(1_003, null, null),
            affectedPrincipals = emptyList(),
            availableOperations = listOf(DELETE_UNWANTED_QUOTAS, IMPORT_CLIENT_QUOTAS),
        )
        val r_c3_e3 = QuotasInspection(
            entity = QuotaEntity.user("u3"),
            clusterIdentifier = "c_3",
            valuesInspection = ValuesInspection(
                ok = false, producerByteRateOk = false, consumerByteRateOk = false, requestPercentageOk = false
            ),
            statusType = UNAVAILABLE,
            expectedQuota = null,
            actualQuota = null,
            affectedPrincipals = emptyList(),
            availableOperations = emptyList(),
        )

        //do actual inspections
        val entitiesResult = inspection.inspectAllClientEntities()
        val unknownEntities = inspection.inspectUnknownClientEntities()
        val c1_result = inspection.inspectClusterQuotas("c_1")
        val c2_result = inspection.inspectClusterQuotas("c_2")
        val c3_result = inspection.inspectClusterQuotas("c_3")

        //check individual cluster+entity status
        assertThat(entitiesResult.findInspection("c_1", QuotaEntity.user("u1"))).isEqualTo(r_c1_e1)
        assertThat(entitiesResult.findInspection("c_2", QuotaEntity.user("u1"))).isEqualTo(r_c2_e1)
        assertThat(entitiesResult.findInspection("c_3", QuotaEntity.user("u1"))).isEqualTo(r_c3_e1)
        assertThat(entitiesResult.findInspection("c_1", QuotaEntity.user("u2"))).isEqualTo(r_c1_e2)
        assertThat(entitiesResult.findInspection("c_2", QuotaEntity.user("u2"))).isEqualTo(r_c2_e2)
        assertThat(entitiesResult.findInspection("c_3", QuotaEntity.user("u2"))).isEqualTo(r_c3_e2)
        assertThat(unknownEntities.findInspection("c_1", QuotaEntity.user("u3"))).isEqualTo(r_c1_e3)
        assertThat(unknownEntities.findInspection("c_2", QuotaEntity.user("u3"))).isEqualTo(r_c2_e3)
        assertThat(unknownEntities.findInspection("c_3", QuotaEntity.user("u3"))).isEqualTo(r_c3_e3)

        //check entity aggregated results
        assertThat(entitiesResult).isEqualTo(
            listOf(
                EntityQuotasInspection(
                    entity = QuotaEntity.user("u1"),
                    quotaDescription = e1_q,
                    clusterInspections = listOf(r_c1_e1, r_c2_e1, r_c3_e1),
                    status = QuotaStatus(ok = true, listOf(OK has 2, NOT_PRESENT_AS_EXPECTED has 1)),
                    availableOperations = emptyList(),
                    affectedPrincipals = emptyMap(),
                ),
                EntityQuotasInspection(
                    entity = QuotaEntity.user("u2"),
                    quotaDescription = e2_q,
                    clusterInspections = listOf(r_c1_e2, r_c2_e2, r_c3_e2),
                    status = QuotaStatus(ok = false, listOf(WRONG_VALUE has 1, MISSING has 1, UNEXPECTED has 1)),
                    availableOperations = listOf(
                        CREATE_MISSING_QUOTAS, DELETE_UNWANTED_QUOTAS, ALTER_WRONG_QUOTAS, EDIT_CLIENT_QUOTAS,
                    ),
                    affectedPrincipals = emptyMap(),
                )
            ),
        )

        assertThat(unknownEntities).isEqualTo(
            listOf(
                EntityQuotasInspection(
                    entity = QuotaEntity.user("u3"),
                    quotaDescription = null,
                    clusterInspections = listOf(r_c1_e3, r_c2_e3, r_c3_e3),
                    status = QuotaStatus(ok = false, listOf(UNKNOWN has 2, UNAVAILABLE has 1)),
                    availableOperations = listOf(DELETE_UNWANTED_QUOTAS, IMPORT_CLIENT_QUOTAS),
                    affectedPrincipals = emptyMap(),
                )
            ),
        )

        //check cluster aggregated results
        assertThat(c1_result).isEqualTo(
            ClusterQuotasInspection(
                clusterIdentifier = "c_1",
                status = QuotaStatus(ok = false, listOf(OK has 1, WRONG_VALUE has 1, UNKNOWN has 1)),
                entityInspections = listOf(r_c1_e1, r_c1_e2, r_c1_e3),
            )
        )
        assertThat(c2_result).isEqualTo(
            ClusterQuotasInspection(
                clusterIdentifier = "c_2",
                status = QuotaStatus(ok = false, listOf(NOT_PRESENT_AS_EXPECTED has 1, MISSING has 1, UNKNOWN has 1)),
                entityInspections = listOf(r_c2_e1, r_c2_e2, r_c2_e3),
            )
        )
        assertThat(c3_result).isEqualTo(
            ClusterQuotasInspection(
                clusterIdentifier = "c_3",
                status = QuotaStatus(ok = false, listOf(OK has 1, UNEXPECTED has 1)),
                entityInspections = listOf(r_c3_e1, r_c3_e2),
            )
        )

        //get suggestion operations
        val e1_edit = suggestion.suggestEdit(QuotaEntity.user("u1").asID())
        val e2_edit = suggestion.suggestEdit(QuotaEntity.user("u2").asID())
        val e3_import = suggestion.suggestImport(QuotaEntity.user("u3").asID())
        assertThatThrownBy {
            suggestion.suggestImport(QuotaEntity.user("u1").asID())
        }.isInstanceOf(KafkistryIllegalStateException::class.java)
        assertThatThrownBy {
            suggestion.suggestImport(QuotaEntity.user("u2").asID())
        }.isInstanceOf(KafkistryIllegalStateException::class.java)
        assertThatThrownBy {
            suggestion.suggestEdit(QuotaEntity.user("u3").asID())
        }.isInstanceOf(KafkistryIllegalStateException::class.java)

        //check suggested values
        assertThat(e1_edit).isEqualTo(e1_q)
        assertThat(e2_edit).isEqualTo(
            newQuota(
                entity = QuotaEntity("u2"),
                presence = Presence(EXCLUDED_CLUSTERS, listOf("c_2")),
                properties = QuotaProperties(1_002, 2_002, 102.0),
                clusterOverrides = mapOf(
                    "c_1" to QuotaProperties(null, 2_002, 102.0)
                )
            )
        )
        assertThat(e3_import).isEqualTo(
            newQuota(
                entity = QuotaEntity("u3"),
                presence = Presence(EXCLUDED_CLUSTERS, listOf("c_3")),
                owner = "",
                properties = QuotaProperties(1_003, null, null),
            )
        )

    }

    private fun List<EntityQuotasInspection>.findInspection(
        cluster: KafkaClusterIdentifier,
        entity: QuotaEntity
    ): QuotasInspection? {
        return find { it.entity == entity }
            ?.clusterInspections
            ?.find { it.clusterIdentifier == cluster }
    }

    private fun KafkaCluster.mockClusterQuotas(
        vararg quotas: ClientQuota,
        stateType: StateType = StateType.VISIBLE
    ) {
        whenever(stateProvider.getLatestState(identifier)).thenReturn(
            StateData(
                stateType = stateType,
                clusterIdentifier = identifier,
                stateTypeName = "entities_quotas",
                lastRefreshTime = System.currentTimeMillis(),
                value = ClusterQuotas(quotas.associate { it.entity.asID() to it.properties })
                    .takeIf { stateType == StateType.VISIBLE },
                kafkistryInstance = "localhost",
            )
        )
    }

    private infix fun QuotasInspectionResultType.has(count: Int) = NamedTypeQuantity(this, count)

}