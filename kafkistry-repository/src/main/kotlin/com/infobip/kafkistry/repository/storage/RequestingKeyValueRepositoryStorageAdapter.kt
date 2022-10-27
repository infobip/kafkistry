package com.infobip.kafkistry.repository.storage

import com.infobip.kafkistry.repository.*
import com.infobip.kafkistry.service.KafkistryStorageException
import com.infobip.kafkistry.yaml.YamlMapper

/**
 * This is a bridge/adapter between RequestingKeyValueRepository and FileStorage.
 * Responsibility is to map each entity to specific file and do the serialization and deserialization of content.
 */
class RequestingKeyValueRepositoryStorageAdapter<T : Any>(
    private val storage: FileStorage,
    private val clazz: Class<T>,
    private val mapper: YamlMapper,
    override val keyIdExtractor: KeyIdExtractor<ID, T>,
) : RequestingKeyValueRepository<ID, T> {

    private val fileNameEncoding = FileNameEncoding()

    private fun T.serialize(): String = mapper.serialize(this)

    private fun ID.toFileName(): String = fileNameEncoding.encode(toString()) + ".yaml"
    private fun String.toId(): ID = fileNameEncoding.decode(this.removeSuffix(".yaml"))

    override fun findAll(): List<T> = storage.listCurrentFiles().map { it.deserialize() }

    override fun existsById(id: ID): Boolean = storage.fileExists(id.toFileName())

    override fun findById(id: ID): T? = storage.readFile(id.toFileName())?.deserialize()

    override fun requestDeleteById(writeContext: WriteContext, id: ID) {
        storage.deleteFile(writeContext, id.toFileName())
    }

    override fun requestDeleteAll(writeContext: WriteContext) = storage.deleteAllFiles(writeContext)

    @Throws(DuplicateKeyException::class)
    override fun requestInsert(writeContext: WriteContext, entity: T) {
        val id = keyIdExtractor(entity)
        if (storage.fileExists(id.toFileName())) {
            throw DuplicateKeyException("There already exists entry with id $id, can't insert")
        }
        write(writeContext, id, entity)
    }

    @Throws(EntryDoesNotExistException::class)
    override fun requestUpdate(writeContext: WriteContext, entity: T) {
        val id = keyIdExtractor(entity).also {
            ensureIdExists(it)
        }
        write(writeContext, id, entity)
    }

    @Throws(EntryDoesNotExistException::class)
    override fun requestUpdateMulti(writeContext: WriteContext, entities: List<T>) {
        val data = entities.associateBy { entity ->
            keyIdExtractor(entity).also {
                ensureIdExists(it)
            }
        }
        writeAll(writeContext, data)
    }

    private fun ensureIdExists(id: ID) {
        if (!storage.fileExists(id.toFileName())) {
            throw EntryDoesNotExistException("There is no entry with id $id to update")
        }
    }

    override fun findPendingRequests(): List<EntityRequests<ID, T>> {
        return storage.listChangingFiles().map { it.mapToEntityRequests() }
    }

    override fun findPendingRequestsById(id: ID): List<ChangeRequest<T>> {
        val name = id.toFileName()
        return storage.listChangingFile(name)
                .let {
                    if (it.isEmpty()) {
                        return emptyList()
                    } else {
                        ChangingFile(name, it)
                    }
                }
                .mapToEntityRequests()
                .changes
    }

    override fun listUpdatesOf(id: ID): List<ChangeRequest<T>> {
        val name = id.toFileName()
        return storage.listFileChanges(name)
                .map { ChangeBranch(it.branch, it.commitChange.type, it.commitChange.oldContent, it.commitChange.newContent, listOf(it.commitChange)) }
                .map { it.mapToChangeRequest(name) }
    }

    override fun listCommits(range: CommitsRange): List<CommitEntityChanges<ID, T>> {
        return storage.listCommits(range)
                .map { (commit, files) ->
                    val entities = files.map { fileChange ->
                        val fileContent = selectContent(fileChange.changeType, fileChange.oldContent, fileChange.newContent)
                        EntityCommitChange(
                                id = fileChange.name.toId(),
                                changeType = fileChange.changeType,
                                fileChange = fileChange,
                                optionalEntity = deserializeOptional(fileContent, fileChange.name)
                        )
                    }
                    CommitEntityChanges(
                            commit = commit,
                            entities = entities
                    )
                }
    }

    private fun StoredFile.deserialize(): T = deserialize(content, name)

    private fun deserialize(content: String, name: String): T {
        val entity = try {
            mapper.deserialize(content, clazz)
        } catch (e: Exception) {
            throw KafkistryStorageException("File '$name' encountered entity deserialization exception", e)
        }
        val id = keyIdExtractor(entity)
        if (id.toFileName() != name) {
            throw KafkistryStorageException("Repository of ${clazz.simpleName} corrupted, miss-matching filename '$name' with entity id '$id'")
        }
        return entity
    }

    private fun ChangingFile.mapToEntityRequests(): EntityRequests<ID, T> {
        val changeRequests = changeBranches.map { it.mapToChangeRequest(name) }
        val id = name.toId()
        return EntityRequests(id, changeRequests)
    }

    private fun ChangeBranch.mapToChangeRequest(fileName: String): ChangeRequest<T> {
        val fileContent = selectContent(changeType, oldContent, newContent)
        val optionalEntity = deserializeOptional(fileContent, fileName)
        return ChangeRequest(changeType, name, commitChanges, optionalEntity)
    }

    private fun selectContent(changeType: ChangeType, oldContent: String?, newContent: String?): String? {
        return when (changeType) {
            ChangeType.ADD -> newContent
            ChangeType.UPDATE -> newContent
            ChangeType.DELETE -> oldContent
            ChangeType.NONE -> newContent
        }
    }

    private fun deserializeOptional(fileContent: String?, fileName: String): OptionalEntity<T> {
        if (fileContent == null) {
            return OptionalEntity.error("file '$fileName' has no content")
        }
        return try {
            val entity = deserialize(fileContent, fileName)
            OptionalEntity.of(entity)
        } catch (ex: Exception) {
            OptionalEntity.error(ex.toString())
        }
    }

    private fun write(writeContext: WriteContext, id: ID, entity: T) {
        storage.writeFile(
                writeContext,
                StoredFile(id.toFileName(), entity.serialize())
        )
    }

    private fun writeAll(writeContext: WriteContext, data: Map<ID, T>) {
        val files = data.map { (id, entity) ->
            StoredFile(id.toFileName(), entity.serialize())
        }
        storage.writeFiles(writeContext, files)
    }


}

typealias ID = String
