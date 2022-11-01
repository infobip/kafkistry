package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.config.GitBranchSelectProperties
import com.infobip.kafkistry.repository.storage.Branch

class GitWriteBranchSelector(
        private val writeToMaster: Boolean = false,
        private val mainBranchName: Branch = "master",
        private val eachCommitToNewBranch: Boolean = false,
        private val branchPerUser: Boolean = true,
        private val branchPerEntity: Boolean = true
) {

    constructor(properties: GitBranchSelectProperties): this(
            writeToMaster = properties.writeToMaster,
            eachCommitToNewBranch = properties.newBranchEachWrite,
            branchPerUser = properties.branchPerUser,
            branchPerEntity = properties.branchPerEntity
    )

    fun selectBranchName(userUsername: String, fileNames: List<String>): Branch {
        return when (fileNames.size) {
            0 -> selectBranchName(userUsername, "[none]")
            1 -> selectBranchName(userUsername, fileNames.first())
            else -> {
                val joined = fileNames.joinToString("_") { it.withoutExtension().onlySimpleChars() }
                if (joined.length < 100) {
                    selectBranchName(userUsername, joined)
                } else {
                    selectBranchName(userUsername, "[bulk-${fileNames.size}-files]" + fileNames.hashCode())
                }
            }
        }
    }

    fun selectBranchName(userUsername: String, fileName: String): Branch {
        if (writeToMaster) {
            return mainBranchName
        }
        return listOfNotNull(
                "update",
                userUsername.takeIf { branchPerUser },
                fileName.cleanFileName().takeIf { branchPerEntity },
                (System.currentTimeMillis() / 1000).toString().takeIf { eachCommitToNewBranch }
        ).joinToString("_")
    }

    private fun String.cleanFileName(): String = withoutExtension().onlySimpleChars()

    private fun String.withoutExtension() =
        replace(Regex("\\.[a-z]{2,4}$"), "")    //remove file extension (.yaml .txt etc...)

    private fun String.onlySimpleChars() =
        replace(Regex("[^a-zA-Z0-9]+"), "-")       //make sure there will be legal characters in branch name
}