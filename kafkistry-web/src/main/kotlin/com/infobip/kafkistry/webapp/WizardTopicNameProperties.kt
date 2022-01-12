package com.infobip.kafkistry.webapp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.topic-wizard.topic-name")
class WizardTopicNameProperties {

    var templateName: String = "defaultWizardTopicName"
    var jsName: String = "defaultWizardTopicName"
    var nameLabel: String = "Check topic name"
}