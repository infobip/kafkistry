package com.infobip.kafkistry.webapp.security.auth

import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.utils.test.newTestFolder
import com.infobip.kafkistry.yaml.YamlMapper
import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.UserRole
import com.infobip.kafkistry.webapp.security.auth.preauth.ACCESS_TOKEN
import com.infobip.kafkistry.webapp.security.auth.preauth.StaticAuthTokenAuthenticatedProcessingFilter
import com.infobip.kafkistry.webapp.security.auth.providers.StaticUsersAuthProvider
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.io.File

internal class StaticUsersTest {

    private fun loadFrom(inputConfigurer: StaticUsersProperties.() -> Unit): StaticUsers {
        return StaticUsers(StaticUsersProperties().apply(inputConfigurer))
    }

    private fun StaticUsers.loginWith(username: String, pass: String): User? {
        return StaticUsersAuthProvider(this).authenticateByUsernamePassword(
            UsernamePasswordAuthenticationToken(username, pass)
        )
    }

    private fun StaticUsers.preAuthWith(token: String): User? {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        return StaticAuthTokenAuthenticatedProcessingFilter(this).getPreAuthenticatedPrincipal(
            MockMvcRequestBuilders.get("http://localhost")
                .header(ACCESS_TOKEN, token)
                .buildRequest(null)
        )
    }

    private class StaticUsersList : ArrayList<StaticUsers.StaticUser>()

    private val rawUsers = "a|p|n|s|a@m|ADMIN|;u|p|n|s|u@m|USER|;s||||s@m|READ_SERVICE|t"
    private val adminUser = User("a", "n", "s", "a@m", UserRole.ADMIN)
    private val userUser = User("u", "n", "s", "u@m", UserRole.USER)
    private val serviceUser = User("s", "", "", "s@m", UserRole.READ_SERVICE)

    private val adminPass = "\$2a\$10\$xL07zCVsDMgK2FlB0SUiz..GwkmDVf2pnB2kO1G49/zJhWsxo09cm"   //encrypted p-y
    private val userPass = "\$2a\$10\$cvl7qc9m1nFEAz4s/IrYmOj/gBEZYttzB6B0BCPbJJ5whhx/0.ZvW"    //encrypted p-y

    private val yaml = """
                ---
                - user:
                    username: "a"
                    firstName: "n"
                    lastName: "s"
                    email: "a@m"
                    role:
                      name: "ADMIN"
                      authorities:
                      - name: "VIEW_DATA"
                      - name: "REQUEST_CLUSTER_UPDATES"
                      - name: "REQUEST_TOPIC_UPDATES"
                      - name: "REQUEST_ACL_UPDATES"
                      - name: "REQUEST_QUOTA_UPDATES"
                      - name: "MANAGE_GIT"
                      - name: "MANAGE_KAFKA"
                      - name: "MANAGE_CONSUMERS"
                      - name: "READ_TOPIC"
                    attributes: {}
                  token: null
                  password: "$adminPass"
                  passwordEncrypted: true
                - user:
                    username: "u"
                    firstName: "n"
                    lastName: "s"
                    email: "u@m"
                    role:
                      name: "USER"
                      authorities:
                      - name: "VIEW_DATA"
                      - name: "REQUEST_CLUSTER_UPDATES"
                      - name: "REQUEST_TOPIC_UPDATES"
                      - name: "REQUEST_ACL_UPDATES"
                      - name: "REQUEST_QUOTA_UPDATES"
                      - name: "MANAGE_CONSUMERS"
                      - name: "READ_TOPIC"
                    attributes: {}
                  token: null
                  password: "$userPass"
                  passwordEncrypted: true
                - user:
                    username: "s"
                    firstName: ""
                    lastName: ""
                    email: "s@m"
                    role:
                      name: "READ_SERVICE"
                      authorities:
                      - name: "VIEW_DATA"
                    attributes: {}
                  token: "t"
                  password: null
                  passwordEncrypted: true
            """.trimIndent()

    @Test
    fun loadUsersInRawFormat() {
        val staticUsers = loadFrom { usersRaw = rawUsers }
        assertThat(staticUsers.loginWith("a", "p")).`as`("Admin login").isEqualTo(adminUser)
        assertThat(staticUsers.loginWith("u", "p")).`as`("User login").isEqualTo(userUser)
        assertThat(staticUsers.loginWith("s", "p")).`as`("Service login").isNull()
        assertThat(staticUsers.preAuthWith("t")).`as`("Service api token").isEqualTo(serviceUser)
        assertThat(staticUsers.preAuthWith("x")).`as`("invalid token").isNull()
    }

    @Test
    fun loadUsersInYamlFormat() {
        val staticUsers = loadFrom { usersYaml = yaml }
        assertThat(staticUsers.loginWith("a", "p-y")).`as`("Admin login").isEqualTo(adminUser)
        assertThat(staticUsers.loginWith("u", "p-y")).`as`("User login").isEqualTo(userUser)
        assertThat(staticUsers.loginWith("s", "p-y")).`as`("Service login").isNull()
        assertThat(staticUsers.preAuthWith("t")).`as`("Service api token").isEqualTo(serviceUser)
        assertThat(staticUsers.preAuthWith("x")).`as`("invalid token").isNull()
    }

    @Test
    fun loadUsersInYamlFormatFromFile() {
        val yamlFile = File(newTestFolder("yaml-users"), "users.yaml")
        FileUtils.writeStringToFile(yamlFile, yaml, Charsets.UTF_8)
        val staticUsers = loadFrom { usersYamlFile = yamlFile.absolutePath }
        assertThat(staticUsers.loginWith("a", "p-y")).`as`("Admin login").isEqualTo(adminUser)
        assertThat(staticUsers.loginWith("u", "p-y")).`as`("User login").isEqualTo(userUser)
        assertThat(staticUsers.loginWith("s", "p-y")).`as`("Service login").isNull()
        assertThat(staticUsers.preAuthWith("t")).`as`("Service api token").isEqualTo(serviceUser)
        assertThat(staticUsers.preAuthWith("x")).`as`("invalid token").isNull()
    }

    @Test
    fun generateUsersYamlFileFromRawFormat() {
        val yamlFile = File(newTestFolder("yaml-users"), "generated-users.yaml")
        assertThat(yamlFile.exists()).`as`("$yamlFile exists").isFalse
        val staticUsers = loadFrom {
            usersRaw = rawUsers
            usersYamlFile = yamlFile.absolutePath
        }
        val staticUsersList = with(YamlMapper()) {
            val generatedYaml = FileUtils.readFileToString(yamlFile, Charsets.UTF_8)
            deserialize(generatedYaml, StaticUsersList::class.java)
        }
        assertThat(staticUsersList).isEqualTo(staticUsers.users)
    }

    private class StaticOwnersList : ArrayList<StaticUsers.OwnerUsers>()

    private val ownerGroupA = StaticUsers.OwnerUsers("A", listOf("u1", "u2"))
    private val ownerGroupB = StaticUsers.OwnerUsers("B", listOf("u3"))

    private val ownersRaw = "A|u1,u2;B|u3"
    private val ownersYaml = """
                ---
                - owner: "A"
                  usernames:
                   - "u1"
                   - "u2"
                - owner: "B"
                  usernames:
                   - "u3"
                """.trimIndent()

    @Test
    fun loadOwnerUsers() {
        val staticUsers = loadFrom { ownerGroupsRaw = ownersRaw }
        assertThat(staticUsers.ownersUsers).isEqualTo(listOf(ownerGroupA, ownerGroupB))
    }

    @Test
    fun loadOwnerUsersYaml() {
        val staticUsers = loadFrom { ownerGroupsYaml = ownersYaml }
        assertThat(staticUsers.ownersUsers).isEqualTo(listOf(ownerGroupA, ownerGroupB))
    }

    @Test
    fun loadOwnerUsersYamlFromFile() {
        val yamlFile = File(newTestFolder("yaml-owners"), "owners.yaml")
        FileUtils.writeStringToFile(yamlFile, ownersYaml, Charsets.UTF_8)
        val staticUsers = loadFrom { ownerGroupsYamlFile = yamlFile.absolutePath }
        assertThat(staticUsers.ownersUsers).isEqualTo(listOf(ownerGroupA, ownerGroupB))
    }

    @Test
    fun generateOwnersYamlFileFromRawFormat() {
        val yamlFile = File(newTestFolder("yaml-owners"), "generated-owners.yaml")
        assertThat(yamlFile.exists()).`as`("$yamlFile exists").isFalse
        val staticUsers = loadFrom {
            ownerGroupsRaw = ownersRaw
            ownerGroupsYamlFile = yamlFile.absolutePath
        }
        val staticOwnersList = with(YamlMapper()) {
            val generatedYaml = FileUtils.readFileToString(yamlFile, Charsets.UTF_8)
            deserialize(generatedYaml, StaticOwnersList::class.java)
        }
        assertThat(staticOwnersList).isEqualTo(staticUsers.ownersUsers)
    }


}