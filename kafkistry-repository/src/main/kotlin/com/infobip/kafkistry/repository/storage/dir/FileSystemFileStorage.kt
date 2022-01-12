package com.infobip.kafkistry.repository.storage.dir

import org.apache.commons.io.FileUtils
import com.infobip.kafkistry.repository.WriteContext
import com.infobip.kafkistry.repository.storage.*
import com.infobip.kafkistry.service.KafkistryStorageException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

class FileSystemFileStorage(
        dirPath: String,
        createDirIfMissing: Boolean = true
) : FileStorage {

    private val dir = File(dirPath)
    private val log = LoggerFactory.getLogger(FileSystemFileStorage::class.java)

    init {
        if (!dir.exists()) {
            log.info("Storage directory '{}' is missing", dirPath)
            if (createDirIfMissing) {
                log.info("Creating missing directory '{}'", dirPath)
                val created = dir.mkdirs()
                if (!created) {
                    throw KafkistryStorageException("Failed to create directory '$dirPath'")
                }
            } else {
                throw KafkistryStorageException("Storage directory '$dirPath' does not exist")
            }
        }
        log.info("Using directory '{}' for storage", dir.absolutePath)
    }

    private fun String.toFile() = File("$dir${File.separator}$this")

    override fun fileExists(name: String): Boolean = name.toFile().exists()

    override fun readFile(name: String): StoredFile? {
        return try {
            val content = FileUtils.readFileToString(name.toFile(), StandardCharsets.UTF_8)
            StoredFile(name, content)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun listCurrentFiles(): List<StoredFile> {
        val files = dir.listFiles() ?: throw KafkistryStorageException("Can't read directory '$dir'")
        return files.asSequence()
                .map { StoredFile(it.name, FileUtils.readFileToString(it, StandardCharsets.UTF_8)) }
                .toList()
    }

    override fun deleteFile(writeContext: WriteContext, name: String) {
        name.toFile().delete()
    }

    override fun deleteAllFiles(writeContext: WriteContext) = FileUtils.cleanDirectory(dir)

    override fun writeFile(writeContext: WriteContext, file: StoredFile) {
        FileUtils.write(file.name.toFile(), file.content, StandardCharsets.UTF_8)
    }

    //regular file system writes are performed immediately in-place, so no pending requests

    override fun listChangingFiles(): List<ChangingFile> = emptyList()

    override fun listChangingFile(name: String): List<ChangeBranch> = emptyList()

    override fun listFileChanges(name: String): List<FileChange> = emptyList()

    override fun listCommits(range: CommitsRange): List<CommitChanges> = emptyList()
}