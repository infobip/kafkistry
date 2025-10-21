package com.infobip.kafkistry.it.cluster_ops.testcontainer

import com.infobip.kafkistry.hostname.HostnameResolver
import java.net.InetAddress
import javax.net.ServerSocketFactory

data class HostPort(val host: String, val ip: String, val port: Int) {
    companion object {
        fun newLocalAvailable() = HostPort(
            host = InetAddress.getLocalHost().hostName,
            ip = InetAddress.getLocalHost().hostAddress,
            port = randomPort(),
        )

        private fun randomPort(): Int {
            val serverSocket = ServerSocketFactory.getDefault().createServerSocket(
                0, 1, InetAddress.getByName("localhost")
            )
            return serverSocket.localPort.also {
                serverSocket.close()
            }
        }
    }
}

fun main() {

}