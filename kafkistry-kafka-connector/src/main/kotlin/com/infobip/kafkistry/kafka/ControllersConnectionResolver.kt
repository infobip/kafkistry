package com.infobip.kafkistry.kafka

interface ControllersConnectionResolver {

    fun resolveQuorumControllersConnection(quorumControllersConnection: String): String

    fun hostsPorts(quorumControllersConnection: String): String = extractHostsPorts(quorumControllersConnection)

    companion object {

        private val nodeRegex = Regex("(?<nodeId>\\d+)@(?<host>.*?):(?<port>\\d+)")

        fun extractHostsPorts(quorumControllersConnection: String): String {
            return nodeRegex.findAll(quorumControllersConnection)
                .map { it.groups["host"]?.value to it.groups["port"]?.value }
                .filter { (host, _) -> host != "0.0.0.0" }
                .joinToString(",") { (host, port) -> "$host:$port"}
        }

        val DEFAULT: ControllersConnectionResolver = object : ControllersConnectionResolver {

            override fun resolveQuorumControllersConnection(quorumControllersConnection: String): String {
                return hostsPorts(quorumControllersConnection)
            }
        }
    }
}