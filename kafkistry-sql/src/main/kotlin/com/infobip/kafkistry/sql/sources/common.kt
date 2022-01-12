@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import org.apache.kafka.clients.admin.ConfigEntry
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EnumType
import javax.persistence.Enumerated

fun Map.Entry<String, String?>.toKafkaConfigEntry() = KafkaConfigEntry().apply {
    key = this@toKafkaConfigEntry.key
    value = this@toKafkaConfigEntry.value
}

fun Map.Entry<String, ConfigValue>.toExistingKafkaConfigEntry() = ExistingConfigEntry().apply {
    val mapEntry = this@toExistingKafkaConfigEntry
    entry = KafkaConfigEntry().apply {
        key = mapEntry.key
        value = mapEntry.value.value
    }
    with(mapEntry.value) {
        this@apply.isDefault = default
        this@apply.isReadOnly = readOnly
        this@apply.isSensitive = sensitive
        configSource = source
    }
}

@Embeddable
class PresenceCluster {
    lateinit var cluster: KafkaClusterIdentifier
}

@Suppress("RedundantGetter")    //for hibernate annotations on get()
@Embeddable
class ExistingConfigEntry {

    lateinit var entry: KafkaConfigEntry

    @Column(nullable = false)
    var isDefault: Boolean? = null
        @Column(name = "isDefault", nullable = false)
        get() = field

    @Column(nullable = false)
    var isReadOnly: Boolean? = null
        @Column(name = "isReadOnly", nullable = false)
        get() = field

    @Column(nullable = false)
    var isSensitive: Boolean? = null
        @Column(name = "isSensitive", nullable = false)
        get() = field

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var configSource: ConfigEntry.ConfigSource? = null
        @Enumerated(EnumType.STRING)
        get() = field
}

@Embeddable
class KafkaConfigEntry {

    lateinit var key: String
    var value: String? = null
}



