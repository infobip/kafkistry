package com.infobip.kafkistry.webapp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.http")
class WebHttpProperties {
    lateinit var rootPath: String
}
