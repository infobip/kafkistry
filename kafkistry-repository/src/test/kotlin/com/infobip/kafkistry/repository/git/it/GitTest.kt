package com.infobip.kafkistry.repository.git.it

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.ListAssert
import org.assertj.core.api.ObjectAssert
import org.assertj.core.groups.Tuple
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.transport.RefSpec
import com.infobip.kafkistry.repository.Committer
import com.infobip.kafkistry.repository.WriteContext
import com.infobip.kafkistry.repository.storage.ChangeType
import com.infobip.kafkistry.repository.storage.Commit
import com.infobip.kafkistry.repository.storage.CommitChange
import com.infobip.kafkistry.repository.storage.StoredFile
import com.infobip.kafkistry.repository.storage.git.GitRepository
import com.infobip.kafkistry.repository.storage.git.GitWriteBranchSelector
import com.infobip.kafkistry.utils.test.newTestFolder
import org.junit.Rule
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.shaded.org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.function.Function

class GitTest {

    /*
     *                 Most tests architecture
     *
     *                       +------------+
     *                       | Docker git |
     *         +------------>|   server   |<------------+
     *         |             +------------+             |
     *         |                                        |
     *    +----+-------+                         +------+-----+
     *    | Local repo |                         | Local repo |
     *    |    git1    |                         |    git2    |
     *    +------------+                         +------------+
     *
     */

    @Rule
    @JvmField
    var gitServer = GitServerContainer()

    class GitServerContainer : GenericContainer<GitServerContainer>("strm/test-git-ssh-server:latest") {
        init {
            withExposedPorts(22)
        }
    }

    @Test
    fun `test any change on remote is visible locally - new branch`() {
        val git1 = newGit()
        val git2 = newGit()
        assertThat(git1.fileExists("dir", "test.txt")).isFalse
        assertThat(git1.listAllBranchesChanges("dir", "test.txt")).isEmpty()
        assertThat(git1.listMainBranchHistory()).isEmpty()

        //new branch, write file, push
        git2.writeFile(WriteContext(user, "test msg"), "dir", "test.txt", "test content")

        //pull
        git1.doRefreshRepository()

        assertThat(git1.fileExists("dir", "test.txt")).isFalse()
        val changesWithFilter = git1.listAllBranchesChanges("dir", "test.txt")
        assertThat(changesWithFilter).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "test.txt", null, "test content", listOf("foo bar")
                )
            )
        assertThat(git1.listAllBranchesChanges()).isEqualTo(changesWithFilter)  //because this file is only change against master

        //check all changes on master branch from beginning
        assertThat(git1.listMainBranchChanges().filesChanges).isEmpty() //master has no changes yet
        assertThat(git1.listMainBranchHistory(10)).isEmpty()
    }

    @Test
    fun `try pulling branch with slash in its name`() {
        val git1 = newGit()

        val userSlash = Committer("fbar/fbar", "foo bar", "foo@bar.com")

        git1.writeFile(WriteContext(userSlash, "test msg"), "dir", "test2.txt", "test content2")

        assertThatCode { git1.updateOrInitRepositoryTest() }.doesNotThrowAnyException()
    }


    @Test
    fun `test any change on remote is visible locally - deleted branch`() {
        val git1 = newGit()
        val git2 = newGit()
        git2.writeFile(WriteContext(user, "test msg"), "dir", "test2.txt", "test content2")
        git1.doRefreshRepository()

        git2.doOperation { git, authCallback ->
            git.branchDelete()
                .setForce(true) //because it is a non merged branch
                .setBranchNames("update_fbar_test2")
                .call()
            git.push()  //delete branch on git server
                .setRefSpecs(RefSpec().setSource(null).setDestination("refs/heads/update_fbar_test2"))
                .setRemote("origin")
                .setTransportConfigCallback(authCallback)
                .call()
        }

        assertThat(git1.listAllBranchesChanges()).hasSize(1)
        git1.doRefreshRepository()  //after refresh repo will be aware of remotely deleted branch
        assertThat(git1.listAllBranchesChanges()).isEmpty()

        //check all changes on master branch from beginning
        assertThat(git1.listMainBranchChanges().filesChanges).isEmpty() //changes were not merged into master
        assertThat(git1.listMainBranchHistory(10)).isEmpty()
    }

    @Test
    fun `test any change on remote is visible locally - updated branch`() {
        val git1 = newGit()
        val git2 = newGit()
        git2.writeFile(WriteContext(user, "test msg1"), "dir", "test3.txt", "test content3")
        git1.doRefreshRepository()

        git2.writeFile(WriteContext(user.copy(fullName = "xyz abc"), "test msg2"), "dir", "test3.txt", "edited content")

        assertThat(git1.listAllBranchesChanges()).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "test3.txt", null, "test content3", listOf("foo bar")
                )
            )
        git1.doRefreshRepository()  //after refresh repo will be aware of new commit on the file on same branch
        assertThat(git1.listAllBranchesChanges()).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "test3.txt", null, "edited content", listOf("xyz abc", "foo bar")
                )
            )

        //check all changes on master branch from beginning
        assertThat(git1.listMainBranchChanges().filesChanges).isEmpty() //changes were not merged into master
        assertThat(git1.listMainBranchHistory(10)).isEmpty()
    }

    @Test
    fun `test pull changes from master 1) pull new file 2) pull file update 3) pull file deletion`() {
        val git1 = newGit()
        val git2 = newGit()

        //verify file does not exist
        assertThat(git1.fileExists("dir", "file.txt")).isFalse

        //create new file on master
        git2.doOperation { git, authCallback ->
            val file = File(git.repository.workTree, "dir/file.txt")
            FileUtils.write(file, "new file on master", StandardCharsets.UTF_8)
            git.add().addFilepattern("dir/file.txt").call()
            git.commit()
                .setAuthor("user", "email")
                .setCommitter("user", "email")
                .setMessage("adding new file")
                .call()
            git.push().setTransportConfigCallback(authCallback).call()
        }

        //still does not exist
        assertThat(git1.readFile("dir", "file.txt")).isNull()
        git1.doRefreshRepository()  //1) pull file creation
        //visible after pull
        assertThat(git1.readFile("dir", "file.txt")).isEqualTo(
            StoredFile("file.txt", "new file on master")
        )

        git2.doOperation { git, authCallback ->
            val file = File(git.repository.workTree, "dir/file.txt")
            FileUtils.write(file, "updated file on master", StandardCharsets.UTF_8)
            git.add().addFilepattern("dir/file.txt").call()
            git.commit()
                .setAuthor("user", "email")
                .setCommitter("user", "email")
                .setMessage("updating a file")
                .call()
            git.push().setTransportConfigCallback(authCallback).call()
        }

        git1.doRefreshRepository()  //1) pull file update
        //updated file visible after pull
        assertThat(git1.readFile("dir", "file.txt")).isEqualTo(
            StoredFile("file.txt", "updated file on master")
        )

        git2.doOperation { git, authCallback ->
            git.rm().addFilepattern("dir/file.txt").call()
            git.commit()
                .setAuthor("user", "email")
                .setCommitter("user", "email")
                .setMessage("deleting a file")
                .call()
            git.push().setTransportConfigCallback(authCallback).call()
        }

        git1.doRefreshRepository()  //3) pull file deletion
        assertThat(git1.fileExists("dir", "file.txt")).isFalse()

        val filesCommits = git1.listMainBranchHistory(10)
            .flatMap { (commit, files) ->
                files.map {
                    listOf(
                        commit.username,
                        commit.message,
                        it.name,
                        it.oldContent,
                        it.newContent
                    )
                }
            }
        assertThat(filesCommits).containsExactly(
            listOf("user", "deleting a file", "file.txt", "updated file on master", null),
            listOf("user", "updating a file", "file.txt", "new file on master", "updated file on master"),
            listOf("user", "adding new file", "file.txt", null, "new file on master")
        )
    }

    @Test
    fun `test any change on remote is visible locally - update on branch where local changes would result in failed pull, but hard pull from remote should win`() {
        val git1 = newGit()
        val git2 = newGit()
        git2.writeFile(WriteContext(user, "test msg"), "dir", "test4.txt", "test content4")
        git1.doOperation { git, authCallback ->
            git.checkout()
                .setName("update-fbar")
                .setCreateBranch(true)
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call()
            val file = File(git.repository.workTree, "dir/test4.txt")
            FileUtils.write(file, "local content that will be lost", StandardCharsets.UTF_8)
            git.add().addFilepattern("dir/test4.txt").call()
            try {
                git.pull().setTransportConfigCallback(authCallback).call()
                fail("Expected to have exception on pull")
            } catch (ex: Exception) {
            }
            git.commit()
                .setAuthor("data looser", "email")
                .setCommitter("data looser", "email")
                .setMessage("committing file to have merge conflict")
                .call()
        }
        assertThat(git1.listAllBranchesChanges()).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "test4.txt", null, "local content that will be lost", listOf("data looser")
                )
            )
        git1.doRefreshRepository()  //refresh will do hard pull which will reset to remote state
        assertThat(git1.listAllBranchesChanges()).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "test4.txt", null, "test content4", listOf("foo bar")
                )
            )
    }

    @Test
    fun `test accept update to branch if behind master branch`() {
        val git1 = newGit()
        val writeContext = WriteContext(user, "test msg")
        git1.writeFile(writeContext, "sub-dir", "file.txt", "content")
        git1.doOperation { git, _ ->
            val file = File(git.repository.workTree, "sub-dir/file.txt")
            FileUtils.write(
                file,
                "content to make master ahead of branch and with conflicting merge",
                StandardCharsets.UTF_8
            )
            git.add().addFilepattern("sub-dir/file.txt").call()
            git.commit()
                .setAuthor("master-user", "email")
                .setCommitter("master-user", "email")
                .setMessage("making master ahead of update-fbar branch")
                .call()
        }
        assertThatCode {
            git1.writeFile(writeContext, "sub-dir", "file.txt", "updating while behind master")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `test auto merge master to branch on update if branch is behind master`() {
        val git1 = newGit()
        git1.writeFile(WriteContext(user, "test msg1"), "sub-dir", "file.txt", "content")
        git1.doOperation { git, _ ->
            val file = File(git.repository.workTree, "dir/master.txt")
            FileUtils.write(file, "content to make master ahead of branch", StandardCharsets.UTF_8)
            git.add().addFilepattern("dir/master.txt").call()
            git.commit()
                .setAuthor("master-user", "email")
                .setCommitter("master-user", "email")
                .setMessage("making master ahead of update-fbar branch")
                .call()
        }

        //do merge of master to branch just before update
        git1.writeFile(WriteContext(user, "test msg2"), "sub-dir", "file.txt", "updating while behind master")

        assertThat(git1.readFile("dir", "master.txt")).isEqualTo(
            StoredFile("master.txt", "content to make master ahead of branch")
        )
        assertThat(git1.listAllBranchesChanges()).flatExtractFileChanges()
            .containsExactly(
                tuple(
                    "file.txt",
                    null,
                    "updating while behind master",
                    listOf("foo bar", "foo bar" /*2 commits: merge+actual change*/)
                )
            )
    }

    @Test
    fun `test showing all changes on master`() {
        val git = newGit()

        //verify no files
        assertThat(git.listCurrentFiles("dir")).isEmpty()
        assertThat(git.listMainBranchChanges().filesChanges).isEmpty()

        git.writeFileCommitMasterPush("dir/file1.txt", "content-1-a", "msg 1")
        assertThat(git.listMainBranchChanges().filesChanges.map { it.name }).containsExactly("file1.txt")
        assertThat(git.listMainBranchChanges().filesChanges.flatMap { it.commitChanges }
            .map { it.ignoreTimeAndId() }).containsExactly(
            commit(message = "msg 1", oldContent = null, newContent = "content-1-a")
        )

        git.writeFileCommitMasterPush("dir/file1.txt", "content-1-b", "msg 2")
        assertThat(git.listMainBranchChanges().filesChanges.map { it.name }).containsExactly("file1.txt")
        assertThat(git.listMainBranchChanges().filesChanges.flatMap { it.commitChanges }
            .map { it.ignoreTimeAndId() }).containsExactly(
            commit(message = "msg 2", oldContent = "content-1-a", newContent = "content-1-b"),
            commit(message = "msg 1", oldContent = null, newContent = "content-1-a")
        )

        git.writeFileCommitMasterPush("dir/file2.txt", "content-2-a", "msg 3")
        assertThat(git.listMainBranchChanges().filesChanges.map { it.name }).containsExactlyInAnyOrder(
            "file1.txt",
            "file2.txt"
        )
        assertThat(git.listMainBranchChanges(fileName = "file1.txt").filesChanges.flatMap { it.commitChanges }
            .map { it.ignoreTimeAndId() }).containsExactly(
            commit(message = "msg 2", oldContent = "content-1-a", newContent = "content-1-b"),
            commit(message = "msg 1", oldContent = null, newContent = "content-1-a")
        )
        assertThat(git.listMainBranchChanges(fileName = "file2.txt").filesChanges.flatMap { it.commitChanges }
            .map { it.ignoreTimeAndId() }).containsExactly(
            commit(message = "msg 3", oldContent = null, newContent = "content-2-a")
        )

        val filesCommits = git.listMainBranchHistory()
            .flatMap { (commit, files) ->
                files.map {
                    listOf(
                        commit.username,
                        commit.message,
                        it.name,
                        it.oldContent,
                        it.newContent
                    )
                }
            }
        assertThat(filesCommits).containsExactly(
            listOf("user", "msg 3", "file2.txt", null, "content-2-a"),
            listOf("user", "msg 2", "file1.txt", "content-1-a", "content-1-b"),
            listOf("user", "msg 1", "file1.txt", null, "content-1-a")
        )
    }

    @Test
    fun `test showing all changes for branch against master`() {
        val git = newGit()

        //verify no files
        assertThat(git.listCurrentFiles("dir")).isEmpty()
        assertThat(git.listMainBranchChanges().filesChanges).isEmpty()

        git.writeFileCommitMasterPush("dir/dummy.txt", "irrelevant", "msg")

        git.writeFile(WriteContext(user, "msg 1", "branch"), "dir", "file.txt", "content first")
        git.writeFile(WriteContext(user, "msg 2", "branch"), "dir", "file.txt", "content second")

        val branchChanges = git.listBranchChanges("branch")
        assertThat(branchChanges.filesChanges)
            .extracting<List<Any?>> { listOf(it.name, it.type, it.oldContent, it.newContent) }
            .containsExactly(
                listOf("file.txt", ChangeType.ADD, null, "content second")
            )
        assertThat(branchChanges.filesChanges[0].commitChanges.map { it.ignoreTimeAndId() }).containsExactly(
            commit(
                username = "foo bar",
                message = "KR write autocommit: msg 2",
                oldContent = "content first",
                newContent = "content second"
            ),
            commit(
                username = "foo bar",
                message = "KR write autocommit: msg 1",
                oldContent = null,
                newContent = "content first"
            )
        )
    }

    @Test
    fun `test local git with no remote`() {
        val git = newGit(
            gitRemoteUri = null,
            auth = GitRepository.Auth.NONE,
        )
        assertThat(git.listCurrentFiles("subDir")).isEmpty()
        git.writeFile(WriteContext(user, "test msg", "master"), "subDir", "local.txt", "local content")
        assertThat(git.listCurrentFiles("subDir")).containsExactly(
            StoredFile("local.txt", "local content")
        )
    }

    @Test
    fun `test recover from remotely deleted non-master branch`() {
        val git1 = newGit()
        git1.writeFile(
            writeContext = WriteContext(user, "c1", "b1"),
            subDir = "dir", name = "file", content = "content",
        )
        val gitDir = newTestFolder("git")
        val git2 = newGit(dirPath = gitDir)
        git2.doRefreshRepository()
        assertThat(git2.fileExists("dir", "file"))
            .`as`("file should not exist in master branch")
            .isFalse

        assertThat(git1.listBranches()).contains("master", "b1")
        assertThat(git2.listBranches()).contains("master")
        git2.doRefreshRepository()
        assertThat(git2.listBranches()).contains("master", "b1")

        git1.doOperation { git, transportCallback ->
            val b1Commit = git.repository.resolve("b1")
            val mergeResult = git.merge()
                .include(b1Commit).setCommit(true).setMessage("b1 to mater")
                .call()
            assertThat(mergeResult.mergeStatus).isEqualTo(MergeResult.MergeStatus.FAST_FORWARD)
            git.branchDelete().setBranchNames("b1").call()
            git.push().setPushAll().setTransportConfigCallback(transportCallback).call()
        }
        assertThat(git1.listBranches()).contains("master")
        assertThat(git2.fileExists("dir", "file"))
            .`as`("file should not exist in master branch not pulled merge yet")
            .isFalse
        assertThat(git2.listBranches()).contains("master", "b1")
        git1.writeFile(
            writeContext = WriteContext(user, "c2", "master"),
            subDir = "dir", name = "file", content = "content2",
        )

        val git3 = newGit(dirPath = gitDir)
        assertThat(git3.listBranches()).contains("master")

        assertThat(git3.fileExists("dir", "file"))
            .`as`("file should exist after pulling merged b1 into master")
            .isTrue
    }

    private fun newGit(
        dirPath: String = newTestFolder("git"),
        gitRemoteUri: String? = "ssh://git@localhost:${gitServer.getMappedPort(22)}/repo.git",
        auth: GitRepository.Auth = GitRepository.Auth(password = "secret"),
        writeBranchSelector: GitWriteBranchSelector = GitWriteBranchSelector(writeToMaster = false),
        strictSshHostKeyChecking: Boolean = false,
    ): GitRepository {
        return GitRepository(
            dirPath = dirPath,
            gitRemoteUri = gitRemoteUri,
            auth = auth,
            writeBranchSelector = writeBranchSelector,
            strictSshHostKeyChecking = strictSshHostKeyChecking,
            promProperties = PrometheusMetricsProperties(),
        )
    }

    private val user = Committer("fbar", "foo bar", "foo@bar.com")

    private fun ListAssert<GitRepository.BranchChanges>.flatExtractFileChanges(): AbstractListAssert<*, List<Tuple>, Tuple, ObjectAssert<Tuple>> {
        return this
            .flatExtracting<GitRepository.FileChange> { it.filesChanges }
            .extracting(
                Function { it.name },
                Function { it.oldContent },
                Function { it.newContent },
                Function { it.commitChanges.map { c -> c.commit.username } }
            )
    }

    private fun GitRepository.writeFileCommitMasterPush(
        path: String,
        content: String,
        message: String
    ) {
        doOperation { git, authCallback ->
            val file = File(git.repository.workTree, path)
            FileUtils.write(file, content, StandardCharsets.UTF_8)
            git.add().addFilepattern(path).call()
            git.commit()
                .setAuthor("user", "email")
                .setCommitter("user", "email")
                .setMessage(message)
                .call()
            git.push().setTransportConfigCallback(authCallback).call()
        }
    }

    private fun CommitChange.ignoreTimeAndId(): CommitChange = copy(commit = commit.ignoreTimeAndId())
    private fun Commit.ignoreTimeAndId(): Commit = copy(commitId = "", timestampSec = 0)

    private fun commit(
        commitId: String = "",
        merge: Boolean = false,
        username: String = "user",
        timestampSec: Long = 0,
        message: String = "",
        oldContent: String? = null,
        newContent: String? = null
    ): CommitChange = CommitChange(
        commit = Commit(commitId, merge, username, timestampSec, message),
        type = when {
            oldContent != null && newContent != null && oldContent != newContent -> ChangeType.UPDATE
            newContent != null -> ChangeType.ADD
            oldContent != null -> ChangeType.DELETE
            else -> ChangeType.NONE
        },
        oldContent = oldContent,
        newContent = newContent
    )

    private fun GitRepository.updateOrInitRepositoryTest() {
        return javaClass.getDeclaredMethod("updateOrInitRepository").let {
            it.isAccessible = true
            it.invoke(this)
        }
    }

}
