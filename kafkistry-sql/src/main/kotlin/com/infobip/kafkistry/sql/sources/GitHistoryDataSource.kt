@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.AbstractRegistryService
import com.infobip.kafkistry.service.acl.AclsRegistryService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.history.*
import com.infobip.kafkistry.service.quotas.QuotasChange
import com.infobip.kafkistry.service.quotas.QuotasRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.sql.SqlDataSource
import jakarta.persistence.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

abstract class AbstractGitHistoryDataSource<T : AbstractRegistryEntityHistory, C : Change, ID : Any>(
    private val registryService: AbstractRegistryService<ID, *, *, *, C>,
) : SqlDataSource<T> {

    override fun supplyEntities(): List<T> {
        return registryService.getCommitsHistory().toEntities()
    }

    private fun List<ChangeCommit<C>>.toEntities(): List<T> {
        return this.asSequence()
            .flatMap { c -> c.changes.map { it.id to c.commit } }
            .groupBy({ it.first }, { it.second })
            .map { (id, rawCommits) ->
                newEntity(id).apply {
                    firstCommitTime = rawCommits.last().timestampSec.secsToDateTime()
                    lastCommitTime = rawCommits.first().timestampSec.secsToDateTime()
                    commits = rawCommits.map {
                        Commit().apply {
                            time = it.timestampSec.secsToDateTime()
                            commitId = it.commitId
                            mergeCommit = it.merge
                            username = it.username
                            message = it.message
                        }
                    }
                }
            }
    }

    override fun nonJoinColumnNames(): Set<String> = setOf(
        "firstCommitTime", "lastCommitTime", "time", "commitId", "message"
    )

    protected abstract val C.id: ID
    protected abstract fun newEntity(id: ID): T

    private fun Long.secsToDateTime() = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(this), ZoneId.of("UTC")
    )

}

abstract class AbstractRegistryEntityHistory {

    abstract var firstCommitTime: LocalDateTime
    abstract var lastCommitTime: LocalDateTime
    abstract var commits: List<Commit>
}

@Component
class ClustersGitHistoryDataSource(
    clustersRegistryService: ClustersRegistryService
) : AbstractGitHistoryDataSource<ClusterEntityHistory, ClusterChange, KafkaClusterIdentifier>(clustersRegistryService) {
    override val ClusterChange.id: KafkaClusterIdentifier get() = this.identifier
    override fun newEntity(id: KafkaClusterIdentifier) = ClusterEntityHistory().apply { cluster = id }
    override fun modelAnnotatedClass(): Class<ClusterEntityHistory> = ClusterEntityHistory::class.java
}

@Component
class TopicsGitHistoryDataSource(
    topicRegistryService: TopicsRegistryService
) : AbstractGitHistoryDataSource<TopicEntityHistory, TopicChange, TopicName>(topicRegistryService) {
    override val TopicChange.id: TopicName get() = this.topicName
    override fun newEntity(id: TopicName) = TopicEntityHistory().apply { topic = id }
    override fun modelAnnotatedClass(): Class<TopicEntityHistory> = TopicEntityHistory::class.java
}

@Component
class PrincipalAclGitHistoryDataSource(
    aclsRegistryService: AclsRegistryService
) : AbstractGitHistoryDataSource<PrincipalAclEntityHistory, AclsChange, KafkaClusterIdentifier>(aclsRegistryService) {
    override val AclsChange.id: PrincipalId get() = this.principal
    override fun newEntity(id: PrincipalId) = PrincipalAclEntityHistory().apply { principal = id }
    override fun modelAnnotatedClass(): Class<PrincipalAclEntityHistory> = PrincipalAclEntityHistory::class.java
}

@Component
class QuotasGitHistoryDataSource(
    quotasRegistryService: QuotasRegistryService
) : AbstractGitHistoryDataSource<QuotasEntityHistory, QuotasChange, QuotaEntityID>(quotasRegistryService) {
    override val QuotasChange.id: QuotaEntityID get() = this.entityID
    override fun newEntity(id: QuotaEntityID) = QuotasEntityHistory().apply { quotaEntityID = id }
    override fun modelAnnotatedClass(): Class<QuotasEntityHistory> = QuotasEntityHistory::class.java
}


@Entity
@Table(name = "RegistryClustersHistory")
class ClusterEntityHistory : AbstractRegistryEntityHistory() {

    @Id
    lateinit var cluster: KafkaClusterIdentifier
    override lateinit var firstCommitTime: LocalDateTime
    override lateinit var lastCommitTime: LocalDateTime

    @ElementCollection
    @JoinTable(name = "RegistryClustersHistory_Commits")
    override lateinit var commits: List<Commit>
}

@Entity
@Table(name = "RegistryTopicHistory")
class TopicEntityHistory : AbstractRegistryEntityHistory() {

    @Id
    lateinit var topic: TopicName
    override lateinit var firstCommitTime: LocalDateTime
    override lateinit var lastCommitTime: LocalDateTime

    @ElementCollection
    @JoinTable(name = "RegistryTopicHistory_Commits")
    override lateinit var commits: List<Commit>
}

@Entity
@Table(name = "RegistryPrincipalAclsHistory")
class PrincipalAclEntityHistory : AbstractRegistryEntityHistory() {

    @Id
    lateinit var principal: PrincipalId
    override lateinit var firstCommitTime: LocalDateTime
    override lateinit var lastCommitTime: LocalDateTime

    @ElementCollection
    @JoinTable(name = "RegistryPrincipalAclsHistory_Commits")
    override lateinit var commits: List<Commit>
}

@Entity
@Table(name = "RegistryQuotasHistory")
class QuotasEntityHistory : AbstractRegistryEntityHistory() {

    @Id
    lateinit var quotaEntityID: QuotaEntityID
    override lateinit var firstCommitTime: LocalDateTime
    override lateinit var lastCommitTime: LocalDateTime


    @ElementCollection
    @JoinTable(name = "RegistryQuotasHistory_Commits")
    override lateinit var commits: List<Commit>
}

@Embeddable
class Commit {
    lateinit var time: LocalDateTime
    lateinit var commitId: String
    var mergeCommit: Boolean = false
    lateinit var username: String
    lateinit var message: String
}


