package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.model.KafkaClusterIdentifier

class KStreamUrls(base: String) : BaseUrls() {

    companion object {
        const val KSTREAM_APPS = "/kstream-apps"
        const val KSTREAM_APP = "/app"
    }
    private val showAll = Url(base)
    private val showApp = Url("$base$KSTREAM_APP", listOf("clusterIdentifier", "kStreamAppId"))

    fun showAll() = showAll.render()

    fun showKStreamApp(
        clusterIdentifier: KafkaClusterIdentifier,
        kStreamAppId: KStreamAppId,
    ) = showApp.render(
        "clusterIdentifier" to clusterIdentifier,
        "kStreamAppId" to kStreamAppId,
    )
}