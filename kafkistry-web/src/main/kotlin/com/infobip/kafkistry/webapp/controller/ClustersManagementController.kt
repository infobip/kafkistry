package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.*
import com.infobip.kafkistry.kafka.ThrottleRate
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.webapp.url.ClustersManagementUrls.Companion.CLUSTERS_MANAGEMENT
import com.infobip.kafkistry.webapp.url.ClustersManagementUrls.Companion.CLUSTERS_MANAGEMENT_APPLY_THROTTLING
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$CLUSTERS_MANAGEMENT")
class ClustersManagementController(
        private val inspectApi: InspectApi,
) : BaseController() {

    @GetMapping(CLUSTERS_MANAGEMENT_APPLY_THROTTLING)
    fun showApplyThrottling(
            @RequestParam("clusterIdentifier") clusterIdentifier: KafkaClusterIdentifier
    ): ModelAndView {
        val clusterInfo = inspectApi.inspectCluster(clusterIdentifier).let { inspection ->
            inspection.clusterInfo ?: throw KafkistryIllegalStateException(
                    "Can't setup throttling for cluster which has state: " + inspection.clusterState
            )
        }
        val commonThrottle = with(clusterInfo.perBrokerThrottle.values) {
            ThrottleRate(
                    leaderRate = mostFrequentElement { it.leaderRate },
                    followerRate = mostFrequentElement { it.followerRate },
                    alterDirIoRate = mostFrequentElement { it.alterDirIoRate }
            )
        }
        return ModelAndView("clusters/management/throttle", mutableMapOf(
                "clusterInfo" to clusterInfo,
                "commonThrottle" to commonThrottle
        ))
    }


}