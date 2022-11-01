package com.infobip.kafkistry.repository.config

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.repository.*
import com.infobip.kafkistry.repository.storage.CachingKeyValueRepository
import com.infobip.kafkistry.repository.storage.FileStorage
import com.infobip.kafkistry.repository.storage.RequestingKeyValueRepositoryStorageAdapter
import com.infobip.kafkistry.repository.storage.dir.FileSystemFileStorage
import com.infobip.kafkistry.repository.storage.git.GitFileStorage
import com.infobip.kafkistry.repository.storage.git.GitRefreshTrigger
import com.infobip.kafkistry.repository.storage.git.GitRepository
import com.infobip.kafkistry.repository.storage.git.GitWriteBranchSelector
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.yaml.YamlMapper
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.repository.storage.Branch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@ConfigurationProperties("app.repository.git")
class GitRepositoriesProperties {
    var enabled = true
    var localDir: String = "kafkistry/git-repository"
    var remoteSshUri: String = ""
    var sshKeyPath: String? = null
    var sshPrivateKey: String? = null
    var sshKeyPassphrase: String? = null
    var password: String? = null
    var mainBranch: Branch = "master"
    var remoteTimeoutSeconds: Int = 30
    var refreshIntervalSeconds: Int = 30
    var strictSshHostKeyChecking: Boolean = false
    var dropLocalBranchesMissingOnRemote: Boolean = false

    fun refreshIntervalMs() = 1000L * refreshIntervalSeconds
}

@Component
@ConfigurationProperties("app.repository.git.browse")
class GitBrowseProperties {
    var branchBaseUrl = ""
    var commitBaseUrl = ""
}

@ConfigurationProperties("app.repository.git.write-branch-select")
class GitBranchSelectProperties {
    var writeToMaster: Boolean = false
    var newBranchEachWrite: Boolean = false
    var branchPerUser: Boolean = true
    var branchPerEntity: Boolean = true
}

@Configuration
@ConditionalOnProperty("app.repository.git.enabled")
class GitPropertiesConfig {

    @Bean
    fun gitRepositoriesProperties() = GitRepositoriesProperties()

    @Bean
    fun gitBranchSelectProperties() = GitBranchSelectProperties()
}

@Configuration
@ConditionalOnProperty("app.repository.git.enabled")
class GitRepositoriesConfig(
    private val properties: GitRepositoriesProperties,
    private val branchSelectProperties: GitBranchSelectProperties,
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry
) {

    @Bean
    fun topicsFileStorage(gitRepository: GitRepository) = GitFileStorage(gitRepository, "topics")

    @Bean
    fun clustersFileStorage(gitRepository: GitRepository) = GitFileStorage(gitRepository, "clusters")

    @Bean
    fun aclsFileStorage(gitRepository: GitRepository) = GitFileStorage(gitRepository, "acls")

    @Bean
    fun quotasFileStorage(gitRepository: GitRepository) = GitFileStorage(gitRepository, "quotas")

    @Bean
    fun gitWriteBranchSelector() = GitWriteBranchSelector(branchSelectProperties)

    @Bean
    fun gitRepository(
        writeBranchSelector: GitWriteBranchSelector,
        promProperties: PrometheusMetricsProperties,
    ): GitRepository {
        return GitRepository(
            dirPath = properties.localDir,
            gitRemoteUri = properties.remoteSshUri.takeIf { it.isNotBlank() },
            auth = GitRepository.Auth(
                sshKeyPath = properties.sshKeyPath,
                sshPrivateKey = properties.sshPrivateKey,
                sshKeyPassphrase = properties.sshKeyPassphrase,
                password = properties.password
            ),
            writeBranchSelector = writeBranchSelector,
            mainBranch = properties.mainBranch,
            gitTimeoutSeconds = properties.remoteTimeoutSeconds,
            strictSshHostKeyChecking = properties.strictSshHostKeyChecking,
            dropLocalBranchesMissingOnRemote = properties.dropLocalBranchesMissingOnRemote,
            promProperties = promProperties,
        )
    }

    @Bean
    fun refreshTrigger(
        git: GitRepository,
        repositories: List<RequestingKeyValueRepository<*, *>>,
    ) = GitRefreshTrigger(git, repositories, backgroundJobIssuesRegistry)

}

@Component
@ConfigurationProperties("app.repository.dir")
class DirRepositoriesProperties {
    var enabled = false
    var path: String = "kafkistry/dir-repository"
}

@Configuration
@ConditionalOnProperty("app.repository.dir.enabled")
class DirRepositoriesConfig(
    dirRepositoriesProperties: DirRepositoriesProperties
) {

    private val dirPath = dirRepositoriesProperties.path

    @Bean
    fun topicsFileStorage() = FileSystemFileStorage("$dirPath/topics")

    @Bean
    fun clustersFileStorage() = FileSystemFileStorage("$dirPath/clusters")

    @Bean
    fun aclsFileStorage() = FileSystemFileStorage("$dirPath/acls")

    @Bean
    fun quotasFileStorage() = FileSystemFileStorage("$dirPath/quotas")
}

@Component
@ConfigurationProperties("app.repository.caching")
class RepositoryCachingProperties {
    var enabled = true
    var alwaysRefreshAfterMs: Long? = null
}

@Configuration
class RepositoriesConfig(
    private val yamlMapper: YamlMapper,
    private val cachingProperties: RepositoryCachingProperties,
) {

    private fun <ID : Any, T : Any> RequestingKeyValueRepository<ID, T>.configureCaching(): RequestingKeyValueRepository<ID, T> {
        return if (cachingProperties.enabled) {
            CachingKeyValueRepository(this, cachingProperties.alwaysRefreshAfterMs)
        } else {
            this
        }
    }

    @Bean
    fun kafkaTopicsRepository(
        @Qualifier("topicsFileStorage") topicsFileStorage: FileStorage
    ): KafkaTopicsRepository = StorageKafkaTopicsRepository(
        RequestingKeyValueRepositoryStorageAdapter(
            topicsFileStorage, TopicDescription::class.java, yamlMapper, KeyIdExtractor { it.name }
        ).configureCaching()
    )

    @Bean
    fun kafkaClustersRepository(
        @Qualifier("clustersFileStorage") clustersFileStorage: FileStorage
    ): KafkaClustersRepository = StorageKafkaClustersRepository(
        RequestingKeyValueRepositoryStorageAdapter(
            clustersFileStorage, KafkaCluster::class.java, yamlMapper, KeyIdExtractor { it.identifier }
        ).configureCaching()
    )

    @Bean
    fun kafkaAclsRepository(
        @Qualifier("aclsFileStorage") aclFileStorage: FileStorage
    ): KafkaAclsRepository = StorageKafkaAclsRepository(
        RequestingKeyValueRepositoryStorageAdapter(
            aclFileStorage, PrincipalAclRules::class.java, yamlMapper, KeyIdExtractor { it.principal }
        ).configureCaching()
    )

    @Bean
    fun kafkaQuotasRepository(
        @Qualifier("quotasFileStorage") quotasFileStorage: FileStorage
    ): KafkaQuotasRepository = StorageKafkaQuotasRepository(
        RequestingKeyValueRepositoryStorageAdapter(
            quotasFileStorage, QuotaDescription::class.java, yamlMapper, KeyIdExtractor { it.entity.asID() }
        ).configureCaching()
    )

}
