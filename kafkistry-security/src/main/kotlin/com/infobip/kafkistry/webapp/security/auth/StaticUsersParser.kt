package com.infobip.kafkistry.webapp.security.auth

import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.UserRole
import org.springframework.security.crypto.bcrypt.BCrypt

object StaticUsersParser {

    private val roles = listOf(UserRole.ADMIN, UserRole.USER, UserRole.READ_SERVICE).associateBy { it.name }

    fun parseUsers(usersRaw: String): List<StaticUsers.StaticUser>  = usersRaw.parseElements { parseUser(it) }

    fun parseOwners(ownersRaw: String): List<StaticUsers.OwnerUsers> = ownersRaw.parseElements { parseOwnerGroup(it) }

    private fun <T> String.parseElements(elementParser: String.(Int) -> T): List<T> {
        return this
            .split("\n", ";")
            .filter { it.isNotBlank() }
            .mapIndexed { index, element ->
                element.trim().elementParser(index + 1)
            }
    }

    /**
     * username|password|name|surname|email|role|apiKeyToken
     */
    private fun String.parseUser(index: Int): StaticUsers.StaticUser {
        val parts = split("|")
        if (parts.size != 7) {
            throw IllegalArgumentException("Invalid ($index-th) user: expecting 7 parts delimited by |, got '$this'")
        }
        val (username, plainPassword, name, surname, email, roleName, token) = parts
        val role =
            roles[roleName] ?: throw IllegalArgumentException("Invalid ($index-th) user: unresolvable role '$roleName'")
        return StaticUsers.StaticUser(
            user = User(username, name, surname, email, role),
            token = token.takeIf { it.isNotBlank() },
            password = plainPassword.parsePassword(),
            passwordEncrypted = true,
        )
    }

    private fun String.parsePassword(): String? {
        if (this.isBlank()) return null
        try {
            BCrypt.checkpw("irrelevant", this)
            return this //this is already valid hashed password
        } catch (_: Exception) {
            //this is not valid hashed password, hash it
            return BCrypt.hashpw(this, BCrypt.gensalt())
        }
    }

    /**
     * owner|user1,user2,user3
     */
    private fun String.parseOwnerGroup(index: Int): StaticUsers.OwnerUsers {
        val parts = split("|", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid ($index-th) owner group: expecting 2 parts delimited by |, got '$this'")
        }
        val owner = parts[0]
        val users = parts[1].split(",").map { it.trim() }
        return StaticUsers.OwnerUsers(owner, users)
    }

    private operator fun <E> List<E>.component6() = get(5)
    private operator fun <E> List<E>.component7() = get(6)

}

