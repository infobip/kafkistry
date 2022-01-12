package com.infobip.kafkistry.service

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.Presence
import com.infobip.kafkistry.model.PresenceType
import com.infobip.kafkistry.model.Tag
import org.junit.jupiter.api.Test

class ExtensionsKtTest {

    class PresenceTest {

        private val A = ClusterRef("A", emptyList())
        private val B = ClusterRef("B", emptyList())
        private val C = ClusterRef("C", emptyList())
        private val D = ClusterRef("D", emptyList())
        private val E = ClusterRef("E", emptyList())

        private fun ClusterRef.tag(vararg tags: Tag) = copy(tags = tags.toList())

        @Test
        fun empty() {
            val presence = emptyList<ClusterRef>().computePresence(emptyList())
            assertThat(presence).isEqualTo(Presence.ALL)
        }

        @Test
        fun none() {
            val presence = listOf(A, B, C, D, E).computePresence(emptyList())
            assertThat(presence).isEqualTo(Presence(PresenceType.INCLUDED_CLUSTERS, kafkaClusterIdentifiers = emptyList()))
        }

        @Test
        fun all() {
            val presence = listOf(A, B, C, D, E).computePresence(listOf("A", "B", "C", "D", "E"))
            assertThat(presence).isEqualTo(Presence(PresenceType.ALL_CLUSTERS))
        }

        @Test
        fun some() {
            val presence = listOf(A, B, C, D, E).computePresence(listOf("C", "D"))
            assertThat(presence).isEqualTo(Presence(PresenceType.INCLUDED_CLUSTERS, kafkaClusterIdentifiers = listOf("C", "D")))
        }

        @Test
        fun some_not() {
            val presence = listOf(A, B, C, D, E).computePresence(listOf("A", "C", "D"))
            assertThat(presence).isEqualTo(Presence(PresenceType.EXCLUDED_CLUSTERS, kafkaClusterIdentifiers = listOf("B", "E")))
        }

        @Test
        fun `all with disabled`() {
            val presence = listOf(A, B, C, D, E).computePresence(listOf("A", "B", "C", "D"), listOf("E"))
            assertThat(presence).isEqualTo(Presence(PresenceType.ALL_CLUSTERS))
        }

        @Test
        fun `some with disabled`() {
            val presence = listOf(A, B, C, D, E).computePresence(listOf("B"), listOf("E"))
            assertThat(presence).isEqualTo(Presence(PresenceType.INCLUDED_CLUSTERS, kafkaClusterIdentifiers = listOf("B", "E")))
        }

        @Test
        fun `matching tagged`() {
            val presence = listOf(
                A.tag("x"),
                B,
                C.tag("x"),
                D.tag("x"),
                E
            ).computePresence(listOf("A", "C", "D"))
            assertThat(presence).isEqualTo(Presence(PresenceType.TAGGED_CLUSTERS, tag = "x"))
        }


    }
}