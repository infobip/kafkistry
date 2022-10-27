package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.config.GitBranchSelectProperties

class GitWriteBranchSelector(
        private val writeToMaster: Boolean = false,
        private val mainBranchName: String = "master",
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

    fun selectBranchName(userUsername: String, fileNames: List<String>): String {
        when (fileNames.size) {
            0 -> selectBranchName(userUsername, "[none]")
            1 -> selectBranchName(userUsername, fileNames.first())
            else -> selectBranchName(userUsername, "[bulk-update]" + fileNames.hashCode())
        }
        return selectBranchName(userUsername, fileNames.joinToString("_"))
    }

    fun selectBranchName(userUsername: String, fileName: String): String {
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

    private fun String.cleanFileName(): String {
        return this.replace(Regex("\\.[a-z]{2,4}$"), "")    //remove file extension (.yaml .txt etc...)
                .replace(Regex("[^a-zA-Z0-9]+"), "-")       //make sure there will be legal characters in branch name
    }
}