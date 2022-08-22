package com.infobip.kafkistry.webapp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.topic-inspect.extension")
class TopicInspectExtensionProperties {

    var templateName: String? = null
    var jsName: String? = null
}