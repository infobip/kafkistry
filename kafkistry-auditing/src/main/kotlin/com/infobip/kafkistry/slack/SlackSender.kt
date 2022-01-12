package com.infobip.kafkistry.slack

import com.github.seratch.jslack.Slack
import com.github.seratch.jslack.SlackConfig
import com.github.seratch.jslack.api.methods.request.chat.ChatPostMessageRequest
import com.github.seratch.jslack.common.http.SlackHttpClient
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.util.concurrent.Executors

@Component
@ConditionalOnProperty("app.slack.enabled")
class SlackSender(slackProperties: SlackProperties) : AutoCloseable {

    private val log = LoggerFactory.getLogger(SlackSender::class.java)

    private val client = with(slackProperties) {
        val httpClient = OkHttpClient.Builder()
            .apply {
                if (proxyHost.isNotBlank() && proxyPort > 0) {
                    log.info("Initializing slack sender over http proxy {}:{}", proxyHost, proxyPort)
                    proxySelector(ProxySelector.of(InetSocketAddress(proxyHost, proxyPort)))
                }
            }
            .build()
        Slack
            .getInstance(SlackConfig.DEFAULT, SlackHttpClient(httpClient))
            .methods(secretToken)
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun sendMessage(messageRequest: ChatPostMessageRequest) {
        executor.submit {
            try {
                val response = client.chatPostMessage(messageRequest)
                if (!response.isOk) {
                    log.error("Sending message to slack is not ok, response: $response")
                }
            } catch (ex: Exception) {
                log.error("Got exception while sending message to slack", ex)
            }
        }
    }

    override fun close() = executor.shutdown()
}
