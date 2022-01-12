package com.infobip.kafkistry.webapp.security.auth

import org.apache.commons.io.FileUtils
import com.infobip.kafkistry.service.existingvalues.ExistingValuesSupplier
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.yaml.YamlMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.io.File

@Component
@ConfigurationProperties("app.security.static")
class StaticUsersProperties {
    var usersYaml = ""
    var usersYamlFile = ""
    var usersRaw = ""
    var ownerGroupsYaml = ""
    var ownerGroupsYamlFile = ""
    var ownerGroupsRaw = ""
}

@Component
class StaticUsers(properties: StaticUsersProperties) : ExistingValuesSupplier {

    private val mapper = YamlMapper()

    val users: List<StaticUser> = with(properties) {
        load(ServiceUsersList::class.java, usersYaml, usersYamlFile, usersRaw, StaticUsersParser::parseUsers)
    }

    val ownersUsers: List<OwnerUsers> = with(properties) {
        load(OwnerUsersList::class.java, ownerGroupsYaml, ownerGroupsYamlFile, ownerGroupsRaw, StaticUsersParser::parseOwners)
    }

    override fun owners(): List<String> = ownersUsers.map { it.owner }

    private fun <T, L : List<T>> load(
        type: Class<L>, yaml: String?, yamlFile: String?, raw: String?, rawParser: (String) -> List<T>
    ): List<T> {
        fun failover(): List<T> = raw?.takeIf { it.isNotBlank() }?.let(rawParser).orEmpty()
        return when {
            !yaml.isNullOrBlank() -> mapper.deserialize(yaml, type)
            !yamlFile.isNullOrBlank() -> {
                val file = File(yamlFile)
                file.takeIf { it.exists() }
                    ?.let { FileUtils.readFileToString(it, Charsets.UTF_8) }
                    ?.takeIf { it.trim().isNotEmpty() }
                    ?.let { mapper.deserialize(it, type) }
                    ?: failover().also {
                        val generatedYaml = mapper.serialize(it)
                        FileUtils.writeStringToFile(File(yamlFile), generatedYaml, Charsets.UTF_8)
                    }
            }
            else -> failover()
        }
    }

    data class StaticUser(
            val user: User,
            val token: String?,
            val password: String?,
            val passwordEncrypted: Boolean = false,
    ) {
        override fun toString(): String {
            return "ServiceUser(user=$user, token=${token?.let { "****" }}, password(encrypted=$passwordEncrypted)=${password?.let { "****" }})"
        }
    }

    data class OwnerUsers(
        val owner: String,
        val usernames: List<String>,
    )

    private class ServiceUsersList : ArrayList<StaticUser>()
    private class OwnerUsersList : ArrayList<OwnerUsers>()

}
