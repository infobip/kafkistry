package com.infobip.kafkistry.slack

import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.slack.enabled")
class RegistryUrlFormatter(
    private val appUrl: AppUrl,
    private val properties: SlackProperties,
) : ApplicationListener<WebServerInitializedEvent> {

    private lateinit var server: String

    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        server = properties.baseHost.ifBlank {
            val serverPort = event.webServer.port
            "http://localhost:$serverPort"
        }
    }

    fun generateUrl(path: (AppUrl) -> String): String {
        return server + path(appUrl)
    }

}