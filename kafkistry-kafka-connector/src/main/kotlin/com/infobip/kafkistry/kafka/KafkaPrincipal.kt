package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.PrincipalId

object KafkaPrincipal {

    private const val ANY_USER = "_ANY_"
    private const val ANY_PRINCIPAL = "User:*"
    private const val ANONYMOUS_USER = "_ANONYMOUS_"
    private const val ANONYMOUS_PRINCIPAL = "User:ANONYMOUS"

    fun principalNameOf(username: String): PrincipalId = when (username) {
        ANY_USER -> ANY_PRINCIPAL
        ANONYMOUS_USER -> ANONYMOUS_PRINCIPAL
        else -> "User:$username"
    }

    fun usernameOf(principalId: PrincipalId): String  = when (principalId) {
        ANY_PRINCIPAL -> ANY_USER
        ANONYMOUS_PRINCIPAL -> ANONYMOUS_USER
        else -> principalId.removePrefix("User:")
    }
}