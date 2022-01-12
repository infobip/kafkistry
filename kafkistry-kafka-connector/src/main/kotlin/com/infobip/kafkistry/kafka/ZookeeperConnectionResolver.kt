package com.infobip.kafkistry.kafka

interface ZookeeperConnectionResolver {

    fun resolveZkConnection(brokerZkConnection: String): String

    companion object {
        val DEFAULT: ZookeeperConnectionResolver = object : ZookeeperConnectionResolver {
            override fun resolveZkConnection(brokerZkConnection: String): String = brokerZkConnection
        }
    }
}