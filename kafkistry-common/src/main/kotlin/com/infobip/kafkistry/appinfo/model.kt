package com.infobip.kafkistry.appinfo

import com.fasterxml.jackson.annotation.JsonProperty
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
        val closest: Closest,
        val remote: Remote,
    ) {
        data class Build(
            val time: Date,
            val version: String,
        )

        data class Commit(
            val id: Id,
            val time: Date,
            val message: Message,
        ) {
            data class Id(
                val abbrev: String,
                val full: String,
            )
            data class Message(
                val full: String,
            )
        }
        data class Closest(
            val tag: Tag,
        ) {
            data class Tag(
                val commit: Commit,
                val name: String,
            ) {
                data class Commit(
                    val count: Int,
                )
            }
        }
        data class Remote(
            val origin: Origin,
            val browse: Browse,
        ) {
            data class Origin(
                val url: String,
            )
            data class Browse(
                val url: String,
                @JsonProperty("commit-prefix-url")
                val commitPrefixUrl: String,
            )
        }
    }
}
