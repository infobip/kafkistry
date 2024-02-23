package com.infobip.kafkistry.it.broswer

import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.ImageNameSubstitutor

/**
 * Motivation: to be able to run on M1 Apple chips
 * https://github.com/testcontainers/testcontainers-java/issues/5183
 * Could be removed if selenium team decides to build arm64 architecture as non-experimental
 */
class ChromeSeleniumARMSubstitutor  : ImageNameSubstitutor() {

    private val log = LoggerFactory.getLogger(ChromeSeleniumARMSubstitutor::class.java)

    override fun apply(original: DockerImageName): DockerImageName {
        val imageName = original.asCanonicalNameString()
        return if ("selenium/standalone-chrome" in imageName) {
            DockerImageName.parse("seleniarm/standalone-chromium:latest").also {
                log.info("Substituted '$original' with '$it'")
            }
        } else {
            original
        }
    }

    override fun getDescription(): String = "Change selenium/standalone-chrome to seleniarm/standalone-chromium"
}