package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.KStreamAppsApi
import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.webapp.url.KStreamUrls.Companion.KSTREAM_APP
import com.infobip.kafkistry.webapp.url.KStreamUrls.Companion.KSTREAM_APPS
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$KSTREAM_APPS")
class KStreamAppsController(
    private val kStreamAppsApi: KStreamAppsApi,
) : BaseController() {

    @GetMapping
    fun showAll(): ModelAndView {
        val clusterKStreamApps = kStreamAppsApi.allClustersKStreamApps()
        return ModelAndView("kstream/all", mapOf(
            "clusterKStreamApps" to clusterKStreamApps,
        ))
    }

    @GetMapping(KSTREAM_APP)
    fun showKStreamApp(
        @RequestParam("kStreamAppId") kStreamAppId: KStreamAppId,
        @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier,
    ): ModelAndView {
        val kafkaStreamsApp = kStreamAppsApi.kStreamApp(kStreamAppId, clusterIdentifier)
        return ModelAndView("kstream/app", mapOf(
            "clusterIdentifier" to clusterIdentifier,
            "kafkaStreamsApp" to kafkaStreamsApp,
        ))
    }
}