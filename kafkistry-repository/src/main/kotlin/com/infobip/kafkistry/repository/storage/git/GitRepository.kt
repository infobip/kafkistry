package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.metric.MetricHolder
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.repository.Committer
import com.infobip.kafkistry.repository.WriteContext
import com.infobip.kafkistry.repository.storage.*
import com.infobip.kafkistry.service.KafkistryGitException
import com.infobip.kafkistry.utils.deepToString
import io.prometheus.client.Summary
import org.apache.commons.io.FileUtils
import org.apache.sshd.git.transport.GitSshdSessionFactory
import org.eclipse.jgit.api.*
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.InvalidRefNameException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.RevWalkUtils
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider
import org.eclipse.jgit.transport.sshd.JGitKeyCache
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicReference

private val gitExclusiveLockLatenciesHolder = MetricHolder { prefix ->
    //default name: kafkistry_git_lock_latencies
    Summary.build()
        .name(prefix + "git_lock_latencies")
        .help("Latencies on git repo exclusive lock")
        .labelNames("refreshing", "phase")
        .quantile(0.5, 0.05)   // Add 50th percentile (= median) with 5% tolerated error
        .quantile(0.9, 0.01)   // Add 90th percentile with 1% tolerated error
        .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
        .register()
}

class GitRepository(
    dirPath: String,
    private val gitRemoteUri: String? = null,
    private val writeBranchSelector: GitWriteBranchSelector,
    private val auth: Auth = Auth.NONE,
    private val mainBranch: Branch = "master",
    private val gitTimeoutSeconds: Int = 30,
    private val strictSshHostKeyChecking: Boolean = false,
    private val httpsSslVerify: Boolean = true,
    private val dropLocalBranchesMissingOnRemote: Boolean = false,
    private val hardResetLocalBranchCheckoutConflicts: Boolean = false,
    promProperties: PrometheusMetricsProperties,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(GitRepository::class.java)

    private val gitExclusiveLockLatencies = gitExclusiveLockLatenciesHolder.metric(promProperties)
    private val waitLatencies = gitExclusiveLockLatencies.labels("false", "wait")
    private val execLatencies = gitExclusiveLockLatencies.labels("false", "exec")
    private val totalLatencies = gitExclusiveLockLatencies.labels("false", "total")
    private val waitLatenciesRefreshing = gitExclusiveLockLatencies.labels("true", "wait")
    private val execLatenciesRefreshing = gitExclusiveLockLatencies.labels("true", "exec")
    private val totalLatenciesRefreshing = gitExclusiveLockLatencies.labels("true", "total")

    private val lock = Object()
    private val noRemote = gitRemoteUri == null
    private val dir = File(dirPath)
    private val repository: Repository
    private val git: Git
    private val transportCallback: TransportConfigCallback

    private var lastErrorMsg = AtomicReference<String>(null)

    init {
        logConfig()
        createLocalDirIfMissing()
        transportCallback = createTransportSecurityCallback()
        repository = FileRepository(dir.child("/.git"))
        git = Git(repository)
        if (!httpsSslVerify && gitRemoteUri != null) {
            disableSSLVerify(URI(gitRemoteUri))
        }
        refreshRepository()
    }

    override fun close() {
        git.close()
    }

    private fun logConfig() {
        log.info("Initializing git repository instance")
        log.info("Dir path: {}", dir.absolutePath)
        log.info("Main branch: {}", mainBranch)
        log.info("No remote: {}", noRemote)
        log.info("Git remote uri: {}", gitRemoteUri)
        log.info("Git timeout secs: {}", gitTimeoutSeconds)
        log.info("auth.username: {}", auth.username)
        log.info("auth.password: {}", auth.password?.let { "******" })
        log.info("auth.sshKeyPassphrase: {}", auth.sshKeyPassphrase?.let { "******" })
        log.info("auth.sshKeyPath: {}", auth.sshKeyPath)
        log.info("auth.sshPrivateKey: {}", auth.sshPrivateKey?.takeIf { it.length > 100 }?.let { it.substring(0, 80) + "...********" })
        log.info("Strict ssh host key checking: {}", strictSshHostKeyChecking)
        log.info("Https sslVerify: {}", httpsSslVerify)
    }

    private fun createLocalDirIfMissing() {
        if (!dir.exists()) {
            log.info("Creating missing directory for git storage: '{}'", dir.absolutePath)
            val created = dir.mkdirs()
            if (!created) {
                throw KafkistryGitException("Failed to create missing directory for git: ${dir.absolutePath}").also {
                    lastErrorMsg.set(it.toString())
                }
            }
            log.info("Created missing directory for git storage: '{}'", dir.absolutePath)
        }
    }

    private fun disableSSLVerify(gitServer: URI) {
        if (gitServer.scheme == "https") {
            SystemReader.getInstance().openUserConfig(null, FS.DETECTED).apply {
                load()
                setBoolean(
                    "http",
                    "https://" + gitServer.host + ':' + (if (gitServer.port == -1) 443 else gitServer.port),
                    "sslVerify", false
                )
                save()
            }
        }
    }

    private fun createTransportSecurityCallback(): TransportConfigCallback {
        return when {
            auth.sshKeyPath != null || auth.sshPrivateKey != null -> {
                //authenticating with private key (public part of pair is on git server)
                sshPublicKeyTransportSecurityCallback()
            }
            gitRemoteUri != null && auth.password != null -> {
                //authenticating with plain username+password
                sshUsernamePasswordTransportSecurityCallback()
            }
            else -> {
                if (gitRemoteUri != null) {
                    log.warn("Creating GIT transport without SSH KEY nor PASSWORD authentication")
                }
                TransportConfigCallback {}
            }
        }
    }

    private fun sshPublicKeyTransportSecurityCallback(): TransportConfigCallback {
        val sshConfigFile = Files.createTempFile("tmp-ssh-config", "").toFile().also { confFile ->
            confFile.deleteOnExit()
            val sshConfig = StringBuilder().apply {
                append("${SshConstants.HOST} *\n")
                if (auth.sshKeyPath != null) {
                    append("    ${SshConstants.IDENTITY_FILE} ${auth.sshKeyPath}\n")
                }
                if (auth.sshPrivateKey != null) {
                    val keyFile = Files.createTempFile("tmp-ssh-config", "").toFile().also {
                        it.deleteOnExit()
                        FileUtils.write(it, auth.sshPrivateKey, Charsets.UTF_8)
                    }
                    append("    ${SshConstants.IDENTITY_FILE} ${keyFile}\n")
                }
                append("    ${SshConstants.STRICT_HOST_KEY_CHECKING} ${if (strictSshHostKeyChecking) "yes" else "no"}\n")
            }.toString()
            FileUtils.write(confFile, sshConfig, Charsets.UTF_8)
        }
        val sshSessionFactory = SshdSessionFactoryBuilder()
            .setPreferredAuthentications("publickey")
            .setHomeDirectory(FS.DETECTED.userHome())
            .setSshDirectory(File(FS.DETECTED.userHome(), SshConstants.SSH_DIR))
            .setConfigFile { sshConfigFile.absoluteFile }
            .apply {
                auth.sshKeyPassphrase
                    ?.let { UsernamePasswordCredentialsProvider("", it) }
                    ?.let { IdentityPasswordProvider(it) }
                    ?.run { setKeyPasswordProvider { this } }
            }
            .build(JGitKeyCache())
        return TransportConfigCallback { transport ->
            val sshTransport = transport as SshTransport
            sshTransport.sshSessionFactory = sshSessionFactory
        }
    }

    private fun sshUsernamePasswordTransportSecurityCallback(): TransportConfigCallback {
        val credentialsProvider = run {
            val user = auth.username ?: URI.create(gitRemoteUri.orEmpty()).userInfo
            log.info("Username to SSH login is '$user'")
            UsernamePasswordCredentialsProvider(user, auth.password)
        }
        return TransportConfigCallback { transport ->
            when (transport) {
                is SshTransport -> {
                    transport.credentialsProvider = credentialsProvider
                    transport.sshSessionFactory = GitSshdSessionFactory()
                }
                is TransportHttp -> {
                    transport.credentialsProvider = credentialsProvider
                }
            }
        }
    }

    private fun File.child(name: String) = File(this, name)

    private fun repoState(): RepoState {
        if (!dir.exists() || dir.listFiles().orEmpty().isEmpty()) {
            return RepoState.MISSING
        }
        val isGit = RepositoryCache.FileKey.isGitRepository(dir.child("/.git"), FS.detect())
        if (!isGit || repository.fullBranch == null) {
            return RepoState.INVALID
        }
        return RepoState.OK
    }

    enum class RepoState {
        MISSING, INVALID, OK
    }

    private fun updateOrInitRepository() {
        when (repoState()) {
            RepoState.MISSING -> {
                log.info("Local repository does not exist, going to clone")
                try {
                    cloneOrInitNewRepo()
                } catch (e: Exception) {
                    log.warn("Failed to clone repo", e)
                    throw KafkistryGitException("Failed to clone repository", e).also {
                        lastErrorMsg.set(it.deepToString())
                    }
                }
            }
            RepoState.INVALID -> {
                log.warn("Local repository is in invalid/corrupted state")
                try {
                    reCloneRepository()
                } catch (e: Exception) {
                    throw KafkistryGitException("Failed to re-clone repository", e).also {
                        lastErrorMsg.set(it.deepToString())
                    }
                }
            }
            RepoState.OK -> {
                try {
                    doRefreshRepository()
                } catch (e: Exception) {
                    throw KafkistryGitException("Failed to refresh repository", e).also {
                        lastErrorMsg.set(it.deepToString())
                    }
                }
            }
        }
        lastErrorMsg.set(null)
    }

    fun refreshRepository() {
        try {
            updateOrInitRepository()
        } catch (e: Exception) {
            log.error("Update repository attempt failed!", e)
        }
    }

    fun currentCommitId(): CommitId {
        return exclusiveOnMainBranch {
            currentHeadRevision().objectId.name
        }
    }

    fun allBranchCommitIds(): Map<Branch, CommitId> {
        return exclusiveOnMainBranch {
            git.branchList().call()
                .filter { it.objectId != null }
                .associate { it.name.toSimpleBranchName() to it.objectId.name }
        }
    }

    @Throws(GitAPIException::class)
    private fun cloneOrInitNewRepo() {
        exclusive(refreshing = true) {
            if (noRemote) {
                log.info("Going to GIT INIT empty repository in directory $dir")
                Git.init()
                    .setDirectory(dir)
                    .call()
                makeEmptyCommit()
                log.info("Successful GIT INIT empty repository in directory $dir")
            } else {
                log.info("Going to GIT CLONE $gitRemoteUri into directory $dir")
                Git.cloneRepository()
                    .setURI(gitRemoteUri)
                    .setDirectory(dir)
                    .setBranch(mainBranch.toFullBranchName())
                    .setCloneAllBranches(true)
                    .setTransportConfigCallback(transportCallback)
                    .call()
                repository.config.apply {
                    setString("remote", "origin", "prune", "true")
                }
                log.info("Successful GIT CLONE $gitRemoteUri into directory $dir")
                ensureHasCommit()
            }
        }
    }

    private fun makeEmptyCommit() {
        git.commit()
                .setCommitter("kafkistry", "kafkistry")
                .setAuthor("kafkistry", "kafkistry")
                .setAllowEmpty(true)
                .setMessage("Initial empty commit")
                .call()
    }

    fun listBranches(): List<Branch> {
        return git.branchList().call().map { it.name.toSimpleBranchName() }
    }

    @Throws(GitAPIException::class, IOException::class)
    private fun gitHardPull() {
        val remoteBranches = git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()
        remoteBranches.forEach { remoteBranch ->
            log.debug("Checkout to remote branch '{}'", remoteBranch.name)
            val branchName = remoteBranch.name.toSimpleBranchName()
            try {
                checkoutBranch(branchName)
            } catch (e: RefNotFoundException) {
                if (dropLocalBranchesMissingOnRemote) {
                    log.warn("Local branch '{}' not exist on remote (will drop it): {}", branchName, e.toString())
                    return@forEach
                } else {
                    throw e
                }
            } catch (e: CheckoutConflictException) {
                if (hardResetLocalBranchCheckoutConflicts) {
                    log.warn(
                        "Checkout to branch '{}' got conflict: {},\n\tdoing hard reset", branchName, e.deepToString()
                    )
                    git.reset()
                        .setMode(ResetCommand.ResetType.HARD)
                        .setRef(remoteBranch.name)
                        .call()
                } else {
                    throw e
                }
            } catch (e: InvalidRefNameException) {
                log.warn("Local branch '{}' can't get checked into: {}, skipping", branchName, e.toString())
                return@forEach
            }
            val localHeadRevision = currentHeadRevision()
            if (localHeadRevision.objectId.name != remoteBranch.objectId.name) {
                log.info("On branch '{}', local and remote revisions are not the same (local: {}, remote: {}), " +
                        "going to do hard reset to ensure local and remote are on same revision",
                        remoteBranch.name, localHeadRevision.objectId.name, remoteBranch.objectId.name
                )
                git.reset()
                        .setMode(ResetCommand.ResetType.HARD)
                        .setRef(remoteBranch.name)
                        .call()
            } else {
                log.debug("Local and remote of branch '{}' are on the same revision {}",
                        branchName, localHeadRevision.objectId.name
                )
            }
        }
        ensureHasCommit()
        checkoutBranch(mainBranch)
        log.info("Successful GIT PULL!")
    }

    private fun ensureHasCommit() {
        if (!repository.isEmpty())
            return
        log.info("Repository has no commits (is empty), creating new empty commit")
        makeEmptyCommit()
        if (noRemote)
            return
        log.info("Pushing empty commit...")
        git.push()
            .setTransportConfigCallback(transportCallback)
            .call()
    }

    fun mainBranch(): Branch = mainBranch

    fun doRefreshRepository() {
        if (noRemote) {
            return
        }
        exclusive(refreshing = true) {
            fetch()
            try {
                gitHardPull()
            } catch (jGitException: JGitInternalException) {
                if (jGitException.message.containsLockErrorText()) {
                    log.warn("Local repository corrupted: {}, performing delete + clone", dir)
                    reCloneRepository()
                } else {
                    throw jGitException
                }
            }
            deleteBranchesWithNoRemoteBranch()
        }
    }

    fun fileExists(subDir: String, name: String): Boolean {
        return exclusiveOnMainBranch {
            subDir.asSubDirFile().child(name).exists()
        }
    }

    fun readFile(subDir: String, name: String): StoredFile? {
        return exclusiveOnMainBranch {
            val file = subDir.asSubDirFile().child(name)
            try {
                StoredFile(name, FileUtils.readFileToString(file, Charsets.UTF_8))
            } catch (_: FileNotFoundException) {
                null
            } catch (_: NoSuchFileException) {
                null
            }
        }
    }

    fun listCurrentFiles(subDir: String): List<StoredFile> {
        return exclusiveOnMainBranch {
            readAllFiles(subDir)
        }
    }

    fun listBranchFiles(subDir: String, branch: Branch): List<StoredFile> {
        return exclusiveOnBranch(branch) {
            readAllFiles(subDir)
        }
    }

    private fun readAllFiles(subDir: String): List<StoredFile> = subDir.asSubDirFile()
        .listFiles()
        ?.map { StoredFile(it.name, FileUtils.readFileToString(it, Charsets.UTF_8)) }
        ?: throw IOException("Can't read directory $dir/$subDir")

    fun deleteFile(writeContext: WriteContext, subDir: String, name: String) {
        exclusiveOnMainBranch {
            if (!fileExists(subDir, name)) {
                return@exclusiveOnMainBranch
            }
            with(writeContext) {
                doInUserBranch(user, name, "KR delete autocommit: $message", targetBranch) {
                    log.info("User {} deleting file {}/{} and removing it from git", user.fullName, subDir, name)
                    git.rm()
                            .addFilepattern("$subDir/$name")
                            .call()
                }
            }
        }
    }

    fun deleteAllFiles(writeContext: WriteContext, subDir: String) {
        exclusiveOnMainBranch {
            val numberOfFiles = subDir.asSubDirFile().let {
                it.listFiles()?.size ?: throw KafkistryGitException("Can't read directory '$it'")
            }
            if (numberOfFiles == 0) {
                return@exclusiveOnMainBranch
            }
            with(writeContext) {
                doInUserBranch(user, "[all_files]", "KR delete-all autocommit: Deleting all files in $subDir/ $message", targetBranch) {
                    log.info("User {} deleting all files in {} and removing it from git", user.fullName, subDir)
                    git.rm()
                            .addFilepattern("$subDir/*")
                            .call()
                }
            }
        }
    }

    fun writeFile(writeContext: WriteContext, subDir: String, file: StoredFile) {
        exclusiveOnMainBranch {
            with(writeContext) {
                doInUserBranch(user, file.name, "KR write autocommit: $message", targetBranch) {
                    log.info("User {} writing to file {}/{} and adding it to git to track", user.fullName, subDir, file.name)
                    file.writeToDisk(subDir)
                    file.gitAdd(subDir)
                }
            }
        }
    }

    fun writeFiles(writeContext: WriteContext, subDir: String, files: List<StoredFile>) {
        exclusiveOnMainBranch {
            with(writeContext) {
                doInUserBranch(user, files.map { it.name }, "KR write autocommit: $message", targetBranch) {
                    log.info("User {} writing to files {}/{} and adding it to git to track",
                        user.fullName, subDir, files.map { it.name }
                    )
                    files.forEach { it.writeToDisk(subDir) }
                    files.forEach { it.gitAdd(subDir) }
                }
            }
        }
    }

    private fun StoredFile.writeToDisk(subDir: String) {
        FileUtils.write(
            subDir.asSubDirFile().child(name),
            content,
            StandardCharsets.UTF_8
        )
    }

    private fun StoredFile.gitAdd(subDir: String) {
        git.add()
            .addFilepattern("$subDir/$name")
            .call()
    }

    fun listAllBranchesChanges(subDir: String? = null, fileName: String? = null): List<BranchChanges> {
        return exclusive {
            val mainBranchRef = mainBranchRef()
            val branchList = git.branchList().call()
                    .filter { it.name != mainBranchRef.name }
            branchList.map {
                val name = Repository.shortenRefName(it.name)
                val filesChanges = listBranchChanges(mainBranchRef, it, subDir, fileName)
                BranchChanges(name, filesChanges)
            }
        }
    }

    fun listBranchChanges(branchName: Branch, subDir: String? = null, fileName: String? = null): BranchChanges {
        return exclusive {
            val mainBranchRef = mainBranchRef()
            val branchRef = branchRef(branchName)
            val filesChanges = listBranchChanges(mainBranchRef, branchRef, subDir, fileName)
            BranchChanges(branchName, filesChanges)
        }
    }

    fun listMainBranchChanges(subDir: String? = null, fileName: String? = null): BranchChanges {
        val mainBranchRef = mainBranchRef()
        val filesChanges = RevWalk(repository).use { walk ->
            val latestCommit = walk.parseCommit(mainBranchRef.objectId)
            walk.sort(RevSort.REVERSE)
            val latestMasterCommit = walk.parseCommit(mainBranchRef.objectId)
            walk.markStart(latestMasterCommit)
            val firstCommit = walk.next()
            walk.listBranchChanges(latestCommit, firstCommit, subDir, fileName)
        }
        return BranchChanges(mainBranch, filesChanges)
    }

    fun listMainBranchHistory(count: Int? = null, skip: Int = 0, subDir: String? = null): List<CommitFileChanges> {
        return listMainBranchHistory(range = CommitsRange(count, skip), subDir)
    }

    fun listMainBranchHistory(range: CommitsRange = CommitsRange.ALL, subDir: String? = null): List<CommitFileChanges> {
        val mainBranchRef = mainBranchRef()
        return RevWalk(repository).use { walk ->
            val latestMasterCommit = walk.parseCommit(mainBranchRef.objectId)
            walk.markStart(latestMasterCommit)
            val commitsSequence = sequence {
                var commitCount = 0
                while (true) {
                    val revCommit = walk.next()
                    yield(revCommit)
                    commitCount++
                    if (revCommit == null || (range.globalLimit != null && commitCount >= range.globalLimit)) {
                        break
                    }
                }
            }
            commitsSequence
                    .zipWithNext()
                    .filter { walk.commitAffectedFiles(subDir, it.first).isNotEmpty() }
                    .drop(range.skip)
                    .map { (currCommit, prevCommit) -> walk.commitChanges(subDir, currCommit, prevCommit) }
                    .filter { it.files.isNotEmpty() }
                    .let { if (range.count != null) it.take(range.count) else it }
                    .toList()
        }
    }

    fun commitChanges(commitId: CommitId, subDirFilter: String? = null): CommitFileChanges {
        val commitObjectId = repository.resolve("$commitId^{commit}")
                ?: throw KafkistryGitException("Commit id '$commitId' not found")
        return RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(commitObjectId)
            walk.markStart(commit)
            val currCommit = walk.next()
            val prevCommit = walk.next()
            walk.commitChanges(subDirFilter, currCommit, prevCommit)
        }
    }

    private fun RevWalk.commitAffectedFiles(subDirFilter: String? = null, currentCommit: RevCommit): List<Pair<String, String>> {
        return currentCommit.affectedFiles(this).mapNotNull { it.filterFilePath(subDirFilter) }
    }

    private fun RevWalk.commitChanges(subDirFilter: String? = null, currentCommit: RevCommit, prevCommit: RevCommit?): CommitFileChanges {
        val affectedFiles = commitAffectedFiles(subDirFilter, currentCommit)
        val files = affectedFiles
                .map { (subDir, name) ->
                    val filePath = subDir + File.separator + name
                    val oldContent = prevCommit?.let { filePath.contentAtCommit(it) }
                    val newContent = filePath.contentAtCommit(currentCommit)
                    FileCommitChange(
                            name = name,
                            changeType = determineChangeType(oldContent, newContent),
                            oldContent = oldContent,
                            newContent = newContent
                    )
                }
                .filter { it.oldContent != null || it.newContent != null }
        return CommitFileChanges(commit = currentCommit.toCommit(), files = files)
    }

    fun reCloneRepository() {
        if (noRemote) {
            throw KafkistryGitException("Can't delete repository if no remote to re-clone from")
        }
        log.info("Going to completely delete local git dir $dir and re-clone it")
        exclusive(refreshing = true) {
            FileUtils.deleteDirectory(dir)
            cloneOrInitNewRepo()
            fetch()
            gitHardPull()
        }
        log.info("Local repository successfully re-cloned and pulled: {}", dir)
    }

    fun lastRefreshErrorMsg(): String? = lastErrorMsg.get()

    fun doOperation(operation: (Git, TransportConfigCallback) -> Unit) {
        exclusive {
            operation(git, transportCallback)
        }
    }

    private fun mainBranchRef() = branchRef(mainBranch)

    private fun branchRef(branchName: String): Ref {
        return repository.exactRef(Constants.R_HEADS + Repository.shortenRefName(branchName))
                ?: throw KafkistryGitException("can't find ref of branch '$branchName'")
    }

    private fun doInUserBranch(
        user: Committer, fileName: String, commitMessage: String, targetBranch: String?, operation: () -> Unit
    ) = doInUserBranch(user, listOf(fileName), commitMessage, targetBranch, operation)

    private fun doInUserBranch(
        user: Committer, fileNames: List<String>, commitMessage: String, targetBranch: String?, operation: () -> Unit
    ) {
        try {
            val userBranchName = targetBranch ?: writeBranchSelector.selectBranchName(user.username, fileNames)
            val branchAlreadyExists = git.branchList()
                    .call()
                    .map { it.name }
                    .contains("refs/heads/$userBranchName")
            log.info("Checking out to user branch '{}'", userBranchName)
            git.checkout()
                    .setName(userBranchName)
                    .setCreateBranch(!branchAlreadyExists)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .call()
            operation()
            log.info("Committing changes with message: {}", commitMessage)
            git.commit()
                    .setAuthor(user.fullName, user.email)
                    .setCommitter(user.fullName, user.email)
                    .setMessage(commitMessage)
                    .call()
            if (!noRemote) {
                log.info("Pushing changes to remote")
                val pushResults = git.push()
                        .setTransportConfigCallback(transportCallback)
                        .call()
                        .toList()
                log.info("Successfully pushed ${pushResults.size} changes to remote")
                pushResults.forEach { log.info(it.messages) }
            }
        } finally {
            log.info("Checkout-ing back to main branch '{}'", mainBranch)
            checkoutBranch(mainBranch)
        }
    }

    private fun <T> exclusiveOnMainBranch(block: () -> T): T {
        return exclusive {
            val currentBranch = repository.fullBranch
                .also { if (it == null) throw KafkistryGitException("Git is in corrupted state, fullBranch == null") }
                .toSimpleBranchName()
            if (currentBranch != mainBranch) {
                throw KafkistryGitException("Refusing to do operations when not on main branch $mainBranch, currently on $currentBranch")
            }
            block()
        }
    }

    private fun <T> exclusiveOnBranch(branch: Branch, block: () -> T): T {
        return exclusiveOnMainBranch {
            checkoutBranch(branch, failIfDoesNotExist = true)
            try {
                block()
            } finally {
                checkoutBranch(mainBranch)
            }
        }
    }

    private fun <T> exclusive(refreshing: Boolean = false, block: () -> T): T {
        val totalTimer = (if (refreshing) totalLatenciesRefreshing else totalLatencies).startTimer()
        val waitTimer = (if (refreshing) waitLatenciesRefreshing else waitLatencies).startTimer()
        return synchronized(lock) {
            waitTimer.observeDuration()
            val execTimer = (if (refreshing) execLatenciesRefreshing else execLatencies).startTimer()
            try {
                block()
            } finally {
                execTimer.observeDuration()
                totalTimer.observeDuration()
            }
        }
    }

    private fun String.asSubDirFile(): File {
        return dir.child(this).also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    private fun String?.containsLockErrorText() = this
            ?.let { contains("Cannot lock") || contains("Short read of block") }
            ?: false

    private fun currentHeadRevision(): Ref {
        return repository.refDatabase.firstExactRef(Constants.HEAD)
    }

    private fun Repository.isEmpty(): Boolean {
        val headRef = refDatabase.findRef(Constants.HEAD)
        return headRef == null || headRef.objectId == null
    }

    private fun checkoutBranch(branchName: Branch, failIfDoesNotExist: Boolean = false) {
        val branchExists = git.branchList().call().any { it.name.toSimpleBranchName() == branchName }
        if (!branchExists) {
            if (failIfDoesNotExist) {
                throw KafkistryGitException("Tried to checkout into non-existing branch '$branchName'")
            } else {
                log.info("Checkout into branch '$branchName' will need to create new local branch")
            }
        }
        val (startPoint, upstreamMode) = if (noRemote) {
            branchName to CreateBranchCommand.SetupUpstreamMode.NOTRACK
        } else {
            "origin/$branchName" to CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM
        }
        git.checkout()
                .setName(branchName)
                .setUpstreamMode(upstreamMode)
                .setStartPoint(startPoint)
                .setCreateBranch(!branchExists)
                .call()
        log.info("Successful GIT CHECKOUT of branch $branchName for repo")
    }

    private fun deleteBranchesWithNoRemoteBranch() {
        val remoteBranchNames = getRemoteBranchNames()
        val localBranchesToDelete = git.branchList().call()
                .map { it.name.toSimpleBranchName() }
                .filter { it !in remoteBranchNames && it != mainBranch }
        if (localBranchesToDelete.isEmpty()) {
            log.debug("No local branches to delete")
        } else {
            log.info("Going to delete local branches that are not present on remote: $localBranchesToDelete")
            try {
                git.branchDelete()
                        .setForce(true) //when false, delete skips non-merged branches
                        .setBranchNames(*localBranchesToDelete.toTypedArray())
                        .call()
                log.info("Deleted local branches that are not present on remote: $localBranchesToDelete")
            } catch (ex: GitAPIException) {
                throw KafkistryGitException("Failed to delete local branches", ex)
            }
        }
    }

    private fun getRemoteBranchNames(): Set<Branch> {
        return git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()
                .map { it.name.toSimpleBranchName() }
                .toSet()
    }

    private fun String.toSimpleBranchName(): Branch = when {
        Constants.R_HEADS in this -> substringAfter(Constants.R_HEADS)
        "origin/" in this -> substringAfter("origin/")
        else -> this
    }

    private fun fetch() {
        val firstRemote = git.remoteList().call().first()
        val refSpecs = firstRemote.fetchRefSpecs
        log.debug("Fetching all branches {}, timeout: {} sec", refSpecs, gitTimeoutSeconds)
        val fetchResult = git.fetch()
                .setTransportConfigCallback(transportCallback)
                .setTimeout(gitTimeoutSeconds)
                .setRefSpecs(refSpecs)
                .call()
        log.debug("Fetch successful for {}", refSpecs)
        fetchResult.messages.forEach { log.debug("Fetch msg: {}", it) }
    }

    private fun String.toFullBranchName() = ensureStartsWith(Constants.R_HEADS)
    private fun String.ensureStartsWith(prefix: String) = if (startsWith(prefix)) this else "$prefix$this"

    @Throws(IOException::class)
    private fun RevCommit.prepareTreeIterator(): AbstractTreeIterator {
        RevWalk(repository).use { walk ->
            val tree = walk.parseTree(tree.id)
            return CanonicalTreeParser().apply {
                repository.newObjectReader().use { reader ->
                    reset(reader, tree.id)
                }
                walk.dispose()
            }
        }
    }

    private fun String.contentAtCommit(commit: RevCommit): String? {
        (TreeWalk.forPath(repository, this, commit.tree) ?: return null)
                .use { treeWalk ->
                    val blobId = treeWalk.getObjectId(0)
                    repository.newObjectReader().use { objectReader ->
                        val objectLoader = objectReader.open(blobId)
                        val bytes = objectLoader.bytes
                        return String(bytes, StandardCharsets.UTF_8)
                    }
                }
    }

    private fun RevCommit.affectedFiles(walk: RevWalk): List<String> {
        val newTree = prepareTreeIterator()
        val oldTree = if (parentCount > 0) {
            val parentCommit = walk.parseCommit(getParent(0))
            parentCommit.prepareTreeIterator()
        } else {
            EmptyTreeIterator()
        }
        val diffs = git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call()
        return diffs.asSequence()
                .flatMap { sequenceOf(it.oldPath, it.newPath) }
                .distinct()
                .toList()
    }

    private fun listBranchChanges(
            baseBranchRef: Ref,
            branchRef: Ref,
            subDirFilter: String?,
            fileNameFilter: String?
    ): List<FileChange> {
        RevWalk(repository).use { walk ->
            val baseCommit = walk.parseCommit(baseBranchRef.objectId)
            val branchCommit = walk.parseCommit(branchRef.objectId)
            val ancestorCommit = walk.findCommonAncestorCommit(baseCommit, branchCommit)
            return walk.listBranchChanges(branchCommit, ancestorCommit, subDirFilter, fileNameFilter)
        }
    }

    private fun RevWalk.listBranchChanges(
            branchCommit: RevCommit,
            ancestorCommit: RevCommit,
            subDirFilter: String?,
            fileNameFilter: String?
    ): List<FileChange> {
        val ancestorTreeIterator = ancestorCommit.prepareTreeIterator()
        val targetTreeIterator = branchCommit.prepareTreeIterator()

        val diffs = git.diff()
                .setOldTree(ancestorTreeIterator)
                .setNewTree(targetTreeIterator)
                .call()
        val filePaths = diffs.asSequence()
                .flatMap { sequenceOf(it.oldPath, it.newPath) }
                .filter { it != "/dev/null" }
                .distinct()
                .toList()
        reset()
        sort(RevSort.TOPO)
        revFilter = RevFilter.ALL
        val filesToCommits = RevWalkUtils.find(this, branchCommit, ancestorCommit)
                .asSequence()
                .map { it to it.affectedFiles(this) }
                .flatMap { (commit, affectedFiles) -> affectedFiles.asSequence().map { it to commit } }
                .groupBy({ it.first }, { it.second })
        return filePaths.asSequence()
                .mapNotNull { filePath ->
                    val (subDir, name) = filePath.filterFilePath(subDirFilter, fileNameFilter) ?: return@mapNotNull null
                    val oldContent = filePath.contentAtCommit(ancestorCommit)
                    val newContent = filePath.contentAtCommit(branchCommit)
                    var prevContent = oldContent
                    val commits = filesToCommits[filePath]
                            ?.reversed()
                            ?.map {
                                val nextContent = filePath.contentAtCommit(it)
                                CommitChange(
                                        commit = it.toCommit(),
                                        type = determineChangeType(prevContent, nextContent),
                                        oldContent = prevContent,
                                        newContent = nextContent
                                ).also { prevContent = nextContent }
                            }
                            ?.reversed()
                            ?: emptyList()
                    FileChange(
                            subDir = subDir,
                            name = name,
                            commitChanges = commits,
                            type = determineChangeType(oldContent, newContent),
                            oldContent = oldContent,
                            newContent = newContent
                    )
                }
                .toList()
    }

    private fun String.filterFilePath(
            subDirFilter: String? = null,
            fileNameFilter: String? = null
    ): Pair<String, String>? {
        if (File.separator !in this) {
            return null
        }
        val (subDir, name) = this.split(File.separator, limit = 2)
        if (this == "/dev/null" || subDirFilter != null && subDir != subDirFilter) {
            return null
        }
        if (fileNameFilter != null && fileNameFilter != name) {
            return null
        }
        return subDir to name
    }

    private fun RevWalk.findCommonAncestorCommit(commit1: RevCommit, commit2: RevCommit): RevCommit {
        reset()
        revFilter = RevFilter.MERGE_BASE
        markStart(commit1)
        markStart(commit2)
        return next()
    }

    private fun determineChangeType(oldContent: String?, newContent: String?): ChangeType {
        return when {
            oldContent != null && newContent != null -> when (oldContent == newContent) {
                true -> ChangeType.NONE
                false -> ChangeType.UPDATE
            }
            newContent != null -> ChangeType.ADD
            oldContent != null -> ChangeType.DELETE
            else -> ChangeType.NONE //throw KafkistryStorageException("Internal problem, seems that git reports changes on file '$subDir/$name' with old and new file content both <null> value")
        }
    }

    private fun RevCommit.toCommit() = Commit(
            commitId = id.name,
            merge = parentCount > 1,
            username = authorIdent.name,
            timestampSec = commitTime.toLong(),
            message = fullMessage
    )

    data class BranchChanges(
            val branchName: Branch,
            val filesChanges: List<FileChange>
    )

    data class FileChange(
            val subDir: String,
            val name: String,
            val commitChanges: List<CommitChange>,
            val type: ChangeType,
            val oldContent: String?,
            val newContent: String?
    )

    data class Auth(
            val sshKeyPath: String? = null,
            val sshPrivateKey: String? = null,
            val sshKeyPassphrase: String? = null,
            val username: String? = null,
            val password: String? = null
    ) {
        companion object {
            val NONE = Auth()
        }
    }

}
