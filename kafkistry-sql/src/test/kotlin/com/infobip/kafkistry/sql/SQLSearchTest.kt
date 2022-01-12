package com.infobip.kafkistry.sql

import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.mock.mock
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.sql.sources.RegistryTopicsDataSource
import com.infobip.kafkistry.utils.test.newTestFolder
import org.junit.Test

class SQLSearchTest {

    private val topicsRegistry: TopicsRegistryService = mock()
    private val clustersRegistry: ClustersRegistryService = mock()

    private val topicsDataSource = RegistryTopicsDataSource(topicsRegistry, clustersRegistry)

    private val repository = SQLiteRepository(
            dbPath = newTestFolder("sql") + "/sql.db",
            autoCreateDir = true,
            resourceLinkDetector = ResourceLinkDetector(),
            sqlDataSources = listOf(topicsDataSource),
    )

    @Test
    fun `insert one and select all`() {
        whenever(clustersRegistry.listClustersRefs()).thenReturn(emptyList())
        whenever(topicsRegistry.listTopics()).thenReturn(listOf(
            newTopic(
                name = "KF.foo",
                description = "none",
                producer = "something",
                owner = "nobody",
            )
        ))
        repository.updateAll(topicsDataSource.supplyEntities())
        val result = repository.query("select * from RegistryTopics")
        assertThat(result).isEqualTo(
                QueryResult(
                        count = 1,
                        totalCount = 1,
                        columns = listOf("topic", "description", "owner", "presenceTag", "presenceType", "producer").map { ColumnMeta(it, "VARCHAR") },
                        columnLinkedType = mapOf(0 to LinkedResource.Type.TOPIC),
                        linkedCompoundTypes = emptyList(),
                        rows = listOf(
                                QueryResultRow(
                                        values = listOf("KF.foo", "none", "nobody", null, "ALL_CLUSTERS", "something"),
                                        linkedResource = LinkedResource(
                                                types = listOf(LinkedResource.Type.TOPIC),
                                                topic = "KF.foo"
                                        )
                                )
                        )
                )
        )
    }

    @Test
    fun `query that yields with empty result set`() {
        val result = repository.query("select * from RegistryTopics where topic='not_existing'")
        assertThat(result.rows).isEmpty()
    }
}