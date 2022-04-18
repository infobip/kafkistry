package com.infobip.kafkistry.service.consumers.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(LagInspectProperties::class)
class LagInspectConfiguration

@ConfigurationProperties("app.lag.threshold")
class LagInspectProperties {

    var partitionTxnLagTolerateAmount: Long = 10
    var partitionNoLagAmount: Long = 100
    var partitionMinorLagAmount: Long = 20_000
    var partitionNoLagTimeSec: Long = 60
    var partitionMinorLagTimeSec: Long = 15 * 60L

    fun noLagAmountRange() = 0L until partitionNoLagAmount
    fun minorAmountLagRange() = partitionNoLagAmount until partitionMinorLagAmount

    fun noLagTimeRange() = 0L until partitionNoLagTimeSec
    fun minorTimeLagRange() = partitionNoLagTimeSec until partitionMinorLagTimeSec

}