package com.infobip.kafkistry.sql

import org.hibernate.dialect.Dialect
import org.hibernate.dialect.function.*
import org.hibernate.type.StandardBasicTypes
import java.sql.Types

class SQLiteDialect : Dialect() {

    override fun supportsLimit(): Boolean = true

    override fun getLimitString(query: String, hasOffset: Boolean): String {
        return StringBuffer(query.length + 20).append(query).append(if (hasOffset) " limit ? offset ?" else " limit ?").toString()
    }

    override fun supportsCurrentTimestampSelection(): Boolean = true

    override fun isCurrentTimestampSelectStringCallable(): Boolean = false

    override fun getCurrentTimestampSelectString(): String = "select current_timestamp"

    override fun supportsUnionAll(): Boolean = true

    override fun hasAlterTable(): Boolean = false // As specify in NHibernate dialect

    override fun dropConstraints(): Boolean = false

    override fun getAddColumnString(): String = "add column"

    override fun getForUpdateString(): String = ""

    override fun supportsOuterJoinForUpdate(): Boolean = false

    override fun getDropForeignKeyString(): String {
        throw UnsupportedOperationException("No drop foreign key syntax supported by SQLiteDialect")
    }

    override fun getAddForeignKeyConstraintString(constraintName: String,
                                                  foreignKey: Array<String>, referencedTable: String, primaryKey: Array<String>,
                                                  referencesPrimaryKey: Boolean): String {
        throw UnsupportedOperationException("No add foreign key syntax supported by SQLiteDialect")
    }

    override fun getAddPrimaryKeyConstraintString(constraintName: String): String {
        throw UnsupportedOperationException("No add primary key syntax supported by SQLiteDialect")
    }

    override fun supportsIfExistsBeforeTableName(): Boolean = true

    override fun supportsCascadeDelete(): Boolean = false

    init {
        registerColumnType(Types.BIT, "integer")
        registerColumnType(Types.TINYINT, "tinyint")
        registerColumnType(Types.SMALLINT, "smallint")
        registerColumnType(Types.INTEGER, "integer")
        registerColumnType(Types.BIGINT, "bigint")
        registerColumnType(Types.FLOAT, "float")
        registerColumnType(Types.REAL, "real")
        registerColumnType(Types.DOUBLE, "double")
        registerColumnType(Types.NUMERIC, "numeric")
        registerColumnType(Types.DECIMAL, "decimal")
        registerColumnType(Types.CHAR, "char")
        registerColumnType(Types.VARCHAR, "varchar")
        registerColumnType(Types.LONGVARCHAR, "longvarchar")
        registerColumnType(Types.DATE, "date")
        registerColumnType(Types.TIME, "time")
        registerColumnType(Types.TIMESTAMP, "timestamp")
        registerColumnType(Types.BINARY, "blob")
        registerColumnType(Types.VARBINARY, "blob")
        registerColumnType(Types.LONGVARBINARY, "blob")
        // registerColumnType(Types.NULL, "null");
        registerColumnType(Types.BLOB, "blob")
        registerColumnType(Types.CLOB, "clob")
        registerColumnType(Types.BOOLEAN, "boolean")
        registerFunction("concat", VarArgsSQLFunction(StandardBasicTypes.STRING, "", "||", ""))
        registerFunction("mod", SQLFunctionTemplate(StandardBasicTypes.INTEGER, "?1 % ?2"))
        registerFunction("quote", StandardSQLFunction("quote", StandardBasicTypes.STRING))
        registerFunction("random", NoArgSQLFunction("random", StandardBasicTypes.INTEGER))
        registerFunction("round", StandardSQLFunction("round"))
        registerFunction("substr", StandardSQLFunction("substr", StandardBasicTypes.STRING))
        registerFunction("trim", object : AbstractAnsiTrimEmulationFunction() {
            override fun resolveBothSpaceTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "trim(?1)")
            override fun resolveBothSpaceTrimFromFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "trim(?2)")
            override fun resolveLeadingSpaceTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "ltrim(?1)")
            override fun resolveTrailingSpaceTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "rtrim(?1)")
            override fun resolveBothTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "trim(?1, ?2)")
            override fun resolveLeadingTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "ltrim(?1, ?2)")
            override fun resolveTrailingTrimFunction(): SQLFunction = SQLFunctionTemplate(StandardBasicTypes.STRING, "rtrim(?1, ?2)")
        })
    }
}