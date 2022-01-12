package com.infobip.kafkistry.webapp.security.auth.providers

import com.infobip.kafkistry.webapp.security.User
import com.infobip.kafkistry.webapp.security.auth.StaticUsers
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class StaticUsersAuthProvider(
        staticUsers: StaticUsers
) : KafkistryUsernamePasswordAuthProvider {

    private val users = staticUsers.users.associateBy { it.user.username }

    override fun authenticateByUsernamePassword(usernamePasswordAuth: UsernamePasswordAuthenticationToken): User? {
        val user = users[usernamePasswordAuth.name] ?: return null
        val plainPassword = usernamePasswordAuth.credentials as? String ?: return null
        val expectedPassword = user.password ?: return null
        if (!validPassword(plainPassword, expectedPassword, user.passwordEncrypted)) {
            return null
        }
        return user.user
    }

    private fun validPassword(plain: String, expected: String, encrypted: Boolean): Boolean {
        return if (encrypted) {
            BCrypt.checkpw(plain, expected)
        } else {
            MessageDigest.isEqual(plain.encodeToByteArray(), expected.encodeToByteArray())
        }
    }

    override fun getOrder(): Int = 1

}