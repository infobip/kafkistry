package com.infobip.kafkistry.appinfo

import java.util.*

data class ModuleBuildInfo(
    val maven: Maven,
    val git: Git,
) {
    data class Maven(
        val groupId: String,
        val artifactId: String,
    )

    data class Git(
        val build: Build,
        val commit: Commit,
    ) {
        data class Build(
            val time: Date,
            val version: String,
        )

        data class Commit(
            val id: Id,
            val time: Date,
        ) {
            data class Id(
                val abbrev: String,
                val full: String,
            )
        }
    }
}
