package com.infobip.kafkistry.webapp.jira

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.jira")
class JiraProperties {
    var baseUrl = ""
}