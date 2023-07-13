package com.infobip.kafkistry.repository.storage

import com.infobip.kafkistry.repository.WriteContext

/**
 * An interface for reading and writing to files
 */
interface FileStorage {

    fun fileExists(name: String): Boolean
    fun readFile(name: String): StoredFile?
    fun listCurrentFiles(): List<StoredFile>
    fun deleteFile(writeContext: WriteContext, name: String)
    fun writeFile(writeContext: WriteContext, file: StoredFile)
    fun writeFiles(writeContext: WriteContext, files: List<StoredFile>)
    fun listChangingFiles(): List<ChangingFile>
    fun listChangingFiles(branch: Branch): List<ChangingFile>
    fun listChangingFile(name: String): List<ChangeBranch>
    fun deleteAllFiles(writeContext: WriteContext)
    fun listFileChanges(name: String): List<FileChange>
    fun listCommits(range: CommitsRange): List<CommitFileChanges>
    fun globallyLastCommitId(): CommitId?
}

typealias Branch = String
typealias CommitId = String

data class StoredFile(
        val name: String,
        val content: String
)

data class ChangingFile(
        val name: String,
        val changeBranches: List<ChangeBranch>
)

data class ChangeBranch(
        val name: Branch,
        val changeType: ChangeType,
        val oldContent: String?,
        val newContent: String?,
        val commitChanges: List<CommitChange>
)

data class Commit(
        val commitId: CommitId,
        val merge: Boolean,
        val username: String,
        val timestampSec: Long,
        val message: String
)

enum class ChangeType {
    ADD, DELETE, UPDATE, NONE
}

data class CommitChange(
        val commit: Commit,
        val type: ChangeType,
        val oldContent: String?,
        val newContent: String?
)

fun CommitChange.toContentChange() = ContentChange(type, oldContent, newContent)

data class ContentChange(
    val type: ChangeType,
    val oldContent: String?,
    val newContent: String?,
)

data class CommitChanges(
    val commit: Commit,
    val changes: List<ContentChange>
)

data class FileChange(
        val branch: Branch,
        val commitChange: CommitChange
)

data class CommitFileChanges(
        val commit: Commit,
        val files: List<FileCommitChange>
)

data class FileCommitChange(
        val name: String,
        val changeType: ChangeType,
        val oldContent: String?,
        val newContent: String?
)

data class CommitsRange(
    val count: Int? = null,
    val skip: Int = 0,
    val globalLimit: Int? = null,
) {
    companion object {
        val ALL = CommitsRange(null, 0, null)
    }
}

