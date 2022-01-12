package com.infobip.kafkistry.sql

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import com.infobip.kafkistry.yaml.YamlMapper
import com.infobip.kafkistry.service.KafkistrySQLException
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteJDBCLoader
import java.io.File
import java.io.InputStreamReader
import java.sql.ResultSet
import java.sql.Types

class SQLiteRepository(
    dbPath: String,
    autoCreateDir: Boolean,
    private val resourceLinkDetector: ResourceLinkDetector,
    private val sqlDataSources: List<SqlDataSource<*>>,
) : SQLRepository {

    private val log = LoggerFactory.getLogger(SQLiteRepository::class.java)

    private val factory: SessionFactory = run {
        log.info("Initializing SQL repository, db-path: {}", dbPath)
        val tmp = File(System.getProperty("java.io.tmpdir"))
        if (!tmp.exists() || !tmp.isDirectory || !tmp.canRead() || !tmp.canWrite()) {
            throw Exception("error with tmpDir='$tmp' exists=${tmp.exists()} directory=${tmp.isDirectory} canRead=${tmp.canRead()} canWrite=${tmp.canWrite()}")
        }
        SQLiteJDBCLoader.initialize()
        val dir = File(dbPath).parentFile
        if (autoCreateDir && dir != null && !dir.exists()) {
            log.info("Parent directory doesn't exist, going to create: {}", dir)
            val created = dir.mkdirs()
            if (created) {
                log.info("Successfully created parent directory: {}", dir)
            } else {
                log.warn("Failed to create parent directory: {}", dir)
            }
        }
        createSessionFactory(dbPath).also {
            log.info("Completed initialization of SQL repository, path: {}", dbPath)
        }
    }

    private fun createSessionFactory(dbPath: String) = Configuration()
        .apply {
            sqlDataSources.forEach { addAnnotatedClass(it.modelAnnotatedClass()) }
        }
        .setProperty("hibernate.connection.url", "jdbc:sqlite:$dbPath")
        .setProperty("hibernate.connection.driver_class", org.sqlite.JDBC::class.java.name)
        .setProperty("hibernate.dialect", SQLiteDialect::class.java.name)
        .setProperty("hibernate.show_sql", "false")
        .setProperty("hibernate.hdm2ddl.auto", "create-drop")
        .setProperty("javax.persistence.schema-generation.database.action", "drop-and-create")
        .buildSessionFactory()

    private val nonJoinColumnNames = setOf(
        "exist", "shouldExist", "status", "description", "configSource", "isDefault", "isReadOnly", "isSensitive"
    ).plus(sqlDataSources.flatMap { it.nonJoinColumnNames() })

    override val tableColumns: List<TableInfo> = factory.openSession().use { session ->
        val tableNames = session.getTableNames()
        val tableColumnNames = tableNames.associateWith { session.getTableColumnNames(it).toSet() }
        tableNames.map { tableName ->
            TableInfo(
                name = tableName,
                joinTable = "_" in tableName,
                columns = session.getTableColumnInfos(tableName, tableColumnNames)
            )
        }
    }

    override val queryExamples: List<QueryExample> = sqlDataSources.flatMap { it.queryExamples() }.plus(
        SQLRepository::class.java.classLoader
            .getResourceAsStream("sql/queryExamples.yaml")
            ?.use { InputStreamReader(it).readText() }
            ?.let { YamlMapper().deserialize(it, QueryExamples::class.java) }
            ?: emptyList()
    )

    class QueryExamples : ArrayList<QueryExample>()

    private fun Session.getTableNames(): List<String> =
        queryExtractValue("SELECT name FROM sqlite_master WHERE type='table'") {
            it.getString("name")
        }

    private fun Session.getTableColumnNames(table: String): List<String> =
        queryExtractValue("PRAGMA table_info($table)") { it.getString("name") }

    private fun Session.getTableColumnInfos(
        table: String,
        allTableColumns: Map<String, Set<String>>
    ): List<ColumnInfo> =
        queryExtractValue("PRAGMA table_info($table)") {
            val column = it.getString("name")
            ColumnInfo(
                name = column,
                type = it.getString("type"),
                primaryKey = it.getInt("pk") > 0,
                joinKey = "_" in column,
                referenceKey = allTableColumns.filterKeys { t -> t != table }.any { (_, columns) ->
                    column in columns && column !in nonJoinColumnNames
                }
            )
        }

    private fun <T> Session.queryExtractValue(sql: String, extract: (ResultSet) -> T): List<T> {
        return doReturningWork { connection ->
            connection.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()
                sequence {
                    while (resultSet.next()) {
                        yield(extract(resultSet))
                    }
                }.toList()
            }
        }
    }

    override fun updateAllLists(objectLists: List<List<Any>>) {
        updateAll(objectLists.flatten())
    }

    fun updateAll(objects: List<Any>) {
        return synchronized(this) {
            withExceptionTranslation {
                doUpdateAll(objects)
            }
        }
    }

    private fun doUpdateAll(objects: List<Any>) {
        factory.openSession().use { session ->
            val tx = session.beginTransaction()
            session.deleteAllFromAllTables()
            objects.forEach { session.persist(it) }
            tx.commit()
        }
    }

    private fun Session.deleteAllFromAllTables() {
        val tableNames = getTableNames()
        doWork { connection ->
            tableNames.forEach { table ->
                @Suppress("SqlWithoutWhere")
                connection.prepareStatement("DELETE from $table").use { it.execute() }
            }
        }
    }

    override fun query(sql: String): QueryResult {
        return synchronized(this) {
            withExceptionTranslation {
                doQuery(sql)
            }
        }
    }

    private fun doQuery(sql: String): QueryResult {
        return factory.openSession().use { session ->
            val count = session.countResultsOf(sql)
            session.doReturningWork { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val resultSet = statement.executeQuery()
                    val columns = with(resultSet.metaData) {
                        (1..columnCount).map {
                            ColumnMeta(
                                name = getColumnName(it),
                                type = getColumnTypeName(it)
                            )
                        }
                    }
                    val resourceFactory = resourceLinkDetector.detectLinkedResource(columns)
                    val rows = sequence {
                        while (resultSet.next()) {
                            val values = (1..resultSet.metaData.columnCount).map { column ->
                                when (resultSet.metaData.getColumnType(column)) {
                                    Types.BOOLEAN -> resultSet.getBoolean(column)
                                        .let { if (resultSet.wasNull()) null else it }
                                    else -> resultSet.getObject(column)
                                }
                            }
                            val linkedResource = resourceFactory.extractLinkedResource(values)
                            yield(QueryResultRow(values, linkedResource))
                        }
                    }.toList()
                    QueryResult(
                        count = rows.size,
                        totalCount = count ?: rows.size,
                        columns = columns,
                        columnLinkedType = resourceFactory.columnLinkedTypes(),
                        linkedCompoundTypes = resourceFactory.compoundLinkedTypes(),
                        rows = rows
                    )
                }
            }
        }
    }

    private fun Session.countResultsOf(sql: String): Int? {
        if (!sql.contains("SELECT", ignoreCase = true)) {
            return null
        }
        val sqlNoLimit = sql.replace(Regex("""\s+LIMIT\s+\d+(\s*,\s*\d+)?(?=$|\s)""", RegexOption.IGNORE_CASE)) { "" }
        return session.doReturningWork { connection ->
            connection.prepareStatement("SELECT count(*) AS count FROM ($sqlNoLimit)").use { statement ->
                val resultSet = statement.executeQuery()
                resultSet.next()
                resultSet.getInt("count")
            }
        }
    }

    private fun <T> withExceptionTranslation(operation: () -> T): T {
        return try {
            operation()
        } catch (ex: Throwable) {
            val message = StringBuilder()
            var throwable: Throwable = ex
            while (true) {
                message.append(throwable.toString())
                throwable = throwable.cause ?: break
                message.append(", caused by: ")
            }
            throw KafkistrySQLException(message.toString(), ex)
        }
    }

    override fun close() {
        synchronized(this) {
            factory.close()
        }
    }
}