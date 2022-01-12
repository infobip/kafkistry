package com.infobip.kafkistry

import org.springframework.boot.runApplication

class KafkistryApplication {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<Kafkistry>(*args) {
                setAdditionalProfiles("defaults")
            }
        }
    }
}
