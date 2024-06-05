package com.infobip.kafkistry.kafka

interface ControllersConnectionResolver {

    fun resolveQuorumControllersConnection(quorumControllersConnection: String): String

    fun hostsPorts(quorumControllersConnection: String): String = extractHostsPorts(quorumControllersConnection)

    companion object {

        private val nodeRegex = Regex("(?<nodeId>\\d+)@(?<host>.*):(?<port>\\d+)}")

        fun extractHostsPorts(quorumControllersConnection: String): String {
            return nodeRegex.findAll(quorumControllersConnection)
                .map { it.groups["host"]?.value + ":" + it.groups["port"]?.value }
                .joinToString(",")
        }

        val DEFAULT: ControllersConnectionResolver = object : ControllersConnectionResolver {

            override fun resolveQuorumControllersConnection(quorumControllersConnection: String): String {
                return hostsPorts(quorumControllersConnection)
            }
        }
    }
}