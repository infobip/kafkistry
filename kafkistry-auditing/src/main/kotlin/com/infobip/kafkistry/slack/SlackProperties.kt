package com.infobip.kafkistry.slack

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.slack")
class SlackProperties {

    var enabled = false

    lateinit var secretToken: String
    var proxyHost = ""
    var proxyPort = 0

    var channelId: String = ""
    var environmentName: String = ""
    var baseHost: String = ""
}