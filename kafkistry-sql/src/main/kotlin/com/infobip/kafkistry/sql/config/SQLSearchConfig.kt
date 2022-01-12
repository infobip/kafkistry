package com.infobip.kafkistry.sql.config

import com.infobip.kafkistry.sql.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.File
import java.util.*

@Configuration
@ConditionalOnProperty("app.sql.enabled", matchIfMissing = true)
class SQLSearchConfig(
    private val sqlProperties: SQLProperties,
    private val sqlDataSources: Optional<List<SqlDataSource<*>>>,
    private val resourceLinkDetector: ResourceLinkDetector
) {

    @Bean
    fun sqlRepository(): SQLRepository {
        val dir = File(sqlProperties.dbDir)
        val primaryFile = File(dir, "primary-sqlite.db").path
        val secondaryFile = File(dir, "secondary-sqlite.db").path
        val externalDataSources = sqlDataSources.orElse(emptyList())
        return RollingSQLRepository(
            primary = SQLiteRepository(primaryFile, sqlProperties.autoCreateDir, resourceLinkDetector, externalDataSources),
            secondary = SQLiteRepository(secondaryFile, sqlProperties.autoCreateDir, resourceLinkDetector, externalDataSources),
        )
    }

}

@Component
@ConfigurationProperties("app.sql")
class SQLProperties {

    var enabled = true
    var dbDir: String = "kafkistry/sqlite"
    var autoCreateDir = true
    @NestedConfigurationProperty
    var clickHouse = ClickHouseProperties()
}

class ClickHouseProperties {
    var openSecurity = false
}