package com.infobip.kafkistry.repository.storage.git

import com.infobip.kafkistry.repository.WriteContext
import com.infobip.kafkistry.repository.storage.*

class GitFileStorage(
        private val gitRepository: GitRepository,
        private val subDir: String
) : FileStorage {

    override fun fileExists(name: String): Boolean = gitRepository.fileExists(subDir, name)

    override fun readFile(name: String): StoredFile? = gitRepository.readFile(subDir, name)

    override fun listCurrentFiles(): List<StoredFile> = gitRepository.listCurrentFiles(subDir)

    override fun deleteFile(writeContext: WriteContext, name: String) {
        gitRepository.deleteFile(writeContext, subDir, name)
    }

    override fun deleteAllFiles(writeContext: WriteContext) {
        gitRepository.deleteAllFiles(writeContext, subDir)
    }

    override fun writeFile(writeContext: WriteContext, file: StoredFile) {
        gitRepository.writeFile(writeContext, subDir, file)
    }

    override fun writeFiles(writeContext: WriteContext, files: List<StoredFile>) {
        gitRepository.writeFiles(writeContext, subDir, files)
    }

    override fun listChangingFiles(): List<ChangingFile> {
        return listChanges()
    }

    override fun listChangingFile(name: String): List<ChangeBranch> {
        return listChanges(name)
                .firstOrNull()
                ?.changeBranches
                ?: emptyList()
    }

    override fun listFileChanges(name: String): List<FileChange> {
        val mainBranch = gitRepository.mainBranch()
        return gitRepository.listMainBranchChanges(subDir, name)
                .filesChanges
                .flatMap { it.commitChanges }
                .map { FileChange(mainBranch, it) }
    }

    override fun listCommits(range: CommitsRange): List<CommitFileChanges> {
        return gitRepository.listMainBranchHistory(range, subDir = subDir)
    }

    private fun listChanges(fileName: String? = null): List<ChangingFile> {
        return gitRepository.listAllBranchesChanges(subDir, fileName).asSequence()
                .flatMap { branchChanges ->
                    branchChanges.filesChanges.asSequence().map {
                        BranchFileChange(branchChanges.branchName, it)
                    }
                }
                .groupBy { it.fileChange.name }
                .mapValues { (_, branchFileChange) ->
                    branchFileChange.associateBy { it.branchName }
                            .map { (branchName, changes) ->
                                ChangeBranch(
                                        name = branchName,
                                        changeType = changes.fileChange.type,
                                        oldContent = changes.fileChange.oldContent,
                                        newContent = changes.fileChange.newContent,
                                        commitChanges = changes.fileChange.commitChanges
                                )
                            }
                }
                .map { (fileName, branchChanges) -> ChangingFile(fileName, branchChanges) }
    }


    private data class BranchFileChange(
            val branchName: Branch,
            val fileChange: GitRepository.FileChange
    )

}