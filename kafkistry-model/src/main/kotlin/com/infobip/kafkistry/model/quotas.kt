package com.infobip.kafkistry.model


//////////////////////////////////
// Quotas
//////////////////////////////////

data class QuotaDescription(
    val entity: QuotaEntity,
    val owner: String,
    val presence: Presence,
    val properties: QuotaProperties,
    val clusterOverrides: Map<KafkaClusterIdentifier, QuotaProperties>,
    val tagOverrides: Map<Tag, QuotaProperties>,
)

typealias KafkaUser = String
typealias QuotaEntityID = String

data class QuotaEntity(
    val user: KafkaUser? = null,
    val clientId: String? = null,
) {
    init {
        if (user == null && clientId == null) {
            throw IllegalArgumentException("Must specify at least one of user, clientId, got null for both")
        }
    }

    fun userIsDefault(): Boolean = user == DEFAULT
    fun userIsAny(): Boolean = user == null
    fun userIsLiteral(): Boolean = !userIsDefault() && !userIsAny()
    fun clientIsDefault(): Boolean = clientId == DEFAULT
    fun clientIsAny(): Boolean = clientId == null

    fun asID(): QuotaEntityID {
        fun String?.encode() = when (this) {
            null -> ALL
            else -> this
        }
        return "${user.encode()}|${clientId.encode()}"
    }

    companion object {
        const val DEFAULT = "<default>"
        const val ALL = "<all>"

        private val DEFAULT_USER = QuotaEntity(user = DEFAULT)
        private val DEFAULT_CLIENT = QuotaEntity(clientId = DEFAULT)
        private val DEFAULT_USER_DEFAULT_CLIENT = QuotaEntity(user = DEFAULT, clientId = DEFAULT)

        fun fromID(quotaEntityID: QuotaEntityID): QuotaEntity {
            val (user, clientId) = quotaEntityID.split("|", limit = 2)
            fun String.decode() = when (this) {
                ALL -> null
                else -> this
            }
            return QuotaEntity(user.decode(), clientId.decode())
        }

        // functions ordered by priority of evaluation

        fun userClient(user: KafkaUser, clientId: String) = QuotaEntity(user, clientId)
        fun userClientDefault(user: KafkaUser): QuotaEntity = userClient(user, DEFAULT)
        fun user(user: KafkaUser) = QuotaEntity(user = user)
        fun userDefaultClient(clientId: String): QuotaEntity = userClient(DEFAULT, clientId)
        fun userDefaultClientDefault(): QuotaEntity = DEFAULT_USER_DEFAULT_CLIENT
        fun userDefault(): QuotaEntity = DEFAULT_USER
        fun client(clientId: String) = QuotaEntity(user = null, clientId = clientId)
        fun clientDefault(): QuotaEntity = DEFAULT_CLIENT
    }
}

data class QuotaProperties(
    val producerByteRate: Long? = null,
    val consumerByteRate: Long? = null,
    val requestPercentage: Double? = null,
) {
    companion object {
        val NONE = QuotaProperties()
    }
}

