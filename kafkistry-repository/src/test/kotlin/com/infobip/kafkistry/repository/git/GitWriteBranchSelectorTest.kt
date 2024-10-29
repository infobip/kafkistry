package com.infobip.kafkistry.repository.git

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import com.infobip.kafkistry.repository.storage.git.GitWriteBranchSelector
import org.junit.jupiter.api.Test

class GitWriteBranchSelectorTest {

    @Test
    fun `test write to master`() {
        newSelector(writeToMaster = true).selectBranch("usrtest", "file-test.txt") shouldBe "master"
    }

    @Test
    fun `each write different branch`() {
        val branch1 = newSelector(newBranchPerWrite = true).selectBranch("usrtest", "file-test.txt")
        Thread.sleep(1001)
        val branch2 = newSelector(newBranchPerWrite = true).selectBranch("usrtest", "file-test.txt")
        branch1 shouldNotBe branch2
    }

    @Test
    fun `test branch per user`() {
        newSelector(branchPerUser = true).selectBranch("usrtest", "file-test.txt") shouldBe "update_usrtest"
        newSelector(branchPerUser = true).selectBranch("xyztest", "file-test.txt") shouldBe "update_xyztest"
        newSelector(branchPerUser = true).selectBranch("usrtest", "topic-test.yaml") shouldBe "update_usrtest"
        newSelector(branchPerUser = true).selectBranch("xyztest", "topic-test.yaml") shouldBe "update_xyztest"
    }

    @Test
    fun `test branch per entity`() {
        newSelector(branchPerEntity = true).selectBranch("usrtest", "file-test.txt") shouldBe "update_file-test"
        newSelector(branchPerEntity = true).selectBranch("xyztest", "file-test.txt") shouldBe "update_file-test"
        newSelector(branchPerEntity = true).selectBranch("usrtest", "topic-test.yaml") shouldBe "update_topic-test"
        newSelector(branchPerEntity = true).selectBranch("xyztest", "topic-test.yaml") shouldBe "update_topic-test"
    }

    @Test
    fun `test branch per user and per entity`() {
        newSelector(branchPerEntity = true, branchPerUser = true).selectBranch("usrtest", "file-test.txt") shouldBe "update_usrtest_file-test"
        newSelector(branchPerEntity = true, branchPerUser = true).selectBranch("xyztest", "file-test.txt") shouldBe "update_xyztest_file-test"
        newSelector(branchPerEntity = true, branchPerUser = true).selectBranch("usrtest", "topic-test.yaml") shouldBe "update_usrtest_topic-test"
        newSelector(branchPerEntity = true, branchPerUser = true).selectBranch("xyztest", "topic-test.yaml") shouldBe "update_xyztest_topic-test"
    }

    private fun newSelector(
            writeToMaster: Boolean = false,
            newBranchPerWrite: Boolean = false,
            branchPerUser: Boolean = false,
            branchPerEntity: Boolean = false
    ) = GitWriteBranchSelector(
            writeToMaster = writeToMaster,
            eachCommitToNewBranch = newBranchPerWrite,
            branchPerUser = branchPerUser,
            branchPerEntity = branchPerEntity
    )

    private fun GitWriteBranchSelector.selectBranch(usr: String, file: String): String {
        return selectBranchName(usr, file)
    }

}