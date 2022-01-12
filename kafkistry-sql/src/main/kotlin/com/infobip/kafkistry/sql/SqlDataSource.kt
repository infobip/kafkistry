package com.infobip.kafkistry.sql

interface SqlDataSource<T : Any> {

    /**
     * [javax.persistence.Entity] annotated class, which instances will be supplied by [supplyEntities]
     * It will be used to create table(s) where supplied instances wil be stored into.
     */
    fun modelAnnotatedClass(): Class<T>

    /**
     * Which column names to blacklist from schema helper marking it as join-able
     */
    fun nonJoinColumnNames(): Set<String> = emptySet()

    /**
     * Get ready-to-use query examples to show them in UI
     */
    fun queryExamples(): List<QueryExample> = emptyList()

    /**
     * Get the data to be inserted to SQL
     */
    fun supplyEntities(): List<T>
}