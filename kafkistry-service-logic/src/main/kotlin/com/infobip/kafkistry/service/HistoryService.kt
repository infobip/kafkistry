package com.infobip.kafkistry.service

import com.infobip.kafkistry.repository.storage.CommitsRange
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springframework.stereotype.Service

@Service
class HistoryService(
    private val clustersRegistry: ClustersRegistryService,
    private val topicsRegistry: TopicsRegistryService,
    private val aclsRegistry: AclsRegistryService,
    private val quotasRegistry: QuotasRegistryService,
) {

    fun getFullHistory(): List<ChangeCommit<out Change>> = getHistory(CommitsRange.ALL)

    fun getRecentHistory(count: Int): List<ChangeCommit<out Change>> = getHistory(CommitsRange(globalLimit = count))

    private fun getHistory(range: CommitsRange): List<ChangeCommit<out Change>> {
        val clusterCommits = clustersRegistry.getCommitsHistory(range)
        val topicsCommits = topicsRegistry.getCommitsHistory(range)
        val aclsCommits = aclsRegistry.getCommitsHistory(range)
        val quotasCommits = quotasRegistry.getCommitsHistory(range)
        return (clusterCommits mergeWith topicsCommits) mergeWith (aclsCommits mergeWith quotasCommits)
    }

    private infix fun List<ChangeCommit<out Change>>.mergeWith(
            commits: List<ChangeCommit<out Change>>
    ): List<ChangeCommit<out Change>> = merge(this, commits) { it.commit.timestampSec }

    private fun <T> merge(
            list1: List<T>,
            list2: List<T>,
            compareBy: (T) -> Number
    ): List<T> {
        return sequence {
            var i1 = 0;
            var i2 = 0
            while (true) {
                if (i1 >= list1.size) {
                    yieldAll(list2.subList(i2, list2.size))
                    break
                }
                if (i2 >= list2.size) {
                    yieldAll(list1.subList(i1, list1.size))
                    break
                }
                val v1 = list1[i1]
                val v2 = list2[i2]
                if (compareBy(v1).toDouble() >= compareBy(v2).toDouble()) {
                    yield(v1)
                    i1++
                } else {
                    yield(v2)
                    i2++
                }
            }
        }.toList()
    }

}