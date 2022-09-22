package com.infobip.kafkistry.service.topic.validation.rules

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.newClusterInfo
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.service.RuleViolation
import org.junit.Test

class MultipleTagsAmbiguousTopicConfigRuleTest {

    private val rule = MultipleTagsAmbiguousTopicConfigRule()

    @Test
    fun `no tags valid`() = assertValid(emptyList(), emptyMap())

    @Test
    fun `single valid`() = assertValid(
        listOf("A"), mapOf(
            "A" to mapOf("x" to "1", "y" to "1")
        )
    )

    @Test
    fun `no conflicts 1 valid`() = assertValid(
        listOf("A"), mapOf(
            "A" to mapOf("x" to "1", "y" to "1"),
            "B" to mapOf("x" to "2", "y" to "2", "z" to "2")
        )
    )

    @Test
    fun `no conflicts 2 valid`() = assertValid(
        listOf("A", "B"), mapOf(
            "A" to mapOf("x" to "1", "y" to "1"),
            "B" to mapOf("x" to "1", "y" to "1", "z" to "2")
        )
    )

    @Test
    fun `conflicts 1 invalid`() = assertInvalid(
        listOf("A", "B"), mapOf(
            "A" to mapOf("x" to "1", "y" to "1"),
            "B" to mapOf("x" to "2", "y" to "1", "z" to "2")
        )
    )

    @Test
    fun `conflicts 1 fix`() {
        val topicDescription = fixConfig(
            listOf("A", "B"), mapOf(
                "A" to mapOf("x" to "1", "y" to "1"),
                "B" to mapOf("x" to "2", "y" to "1", "z" to "2")
            )
        )
        assertThat(topicDescription).isEqualTo(
            newTopic(
                perTagConfigOverrides = mapOf(
                    "A" to mapOf("x" to "1", "y" to "1"),
                    "B" to mapOf("y" to "1", "z" to "2")
                )
            )
        )
    }

    @Test
    fun `conflicts 2 invalid`() = assertInvalid(
        listOf("A", "B", "E"), mapOf(
            "A" to mapOf("x" to "1"),
            "B" to mapOf("x" to "2"),
            "C" to mapOf("x" to "3"),
            "D" to mapOf("x" to "2"),
            "E" to mapOf("x" to "1"),
        )
    )

    @Test
    fun `conflicts 2 fix`() {
        val topicDescription = fixConfig(
            listOf("A", "B", "E"), mapOf(
                "A" to mapOf("x" to "1"),
                "B" to mapOf("x" to "2"),
                "C" to mapOf("x" to "3"),
                "D" to mapOf("x" to "2"),
                "E" to mapOf("x" to "1"),
            )
        )
        assertThat(topicDescription).isEqualTo(
            newTopic(
                perTagConfigOverrides = mapOf(
                    "A" to mapOf("x" to "1"),
                    "C" to mapOf("x" to "3"),
                    "D" to mapOf("x" to "2"),
                    "E" to mapOf("x" to "1"),
                )
            )
        )
    }

    @Test
    fun `conflicts 3 invalid`() = assertInvalid(
        listOf("A", "B", "E", "F"), mapOf(
            "A" to mapOf("x" to "1", "y" to "1", "z" to "1"),
            "B" to mapOf("x" to "2", "y" to "1"),
            "C" to mapOf("x" to "3", "y" to "1"),
            "D" to mapOf("x" to "2", "y" to "3"),
            "E" to mapOf("x" to "1", "z" to "3", "u" to "4"),
            "F" to mapOf("z" to "3", "u" to "5"),
        )
    )

    @Test
    fun `conflicts 3 fix`() {
        val topicDescription = fixConfig(
            listOf("A", "B", "E", "F"), mapOf(
                "A" to mapOf("x" to "1", "y" to "1", "z" to "1"),
                "B" to mapOf("x" to "2", "y" to "1"),
                "C" to mapOf("x" to "3", "y" to "1"),
                "D" to mapOf("x" to "2", "y" to "3"),
                "E" to mapOf("x" to "1", "z" to "3", "u" to "4"),
                "F" to mapOf("z" to "3", "u" to "5"),
            )
        )
        assertThat(topicDescription).isEqualTo(
            newTopic(
                perTagConfigOverrides = mapOf(
                    "A" to mapOf("x" to "1", "y" to "1", "z" to "1"),
                    "B" to mapOf("y" to "1"),
                    "C" to mapOf("x" to "3", "y" to "1"),
                    "D" to mapOf("x" to "2", "y" to "3"),
                    "E" to mapOf("x" to "1", "u" to "4"),
                )
            )
        )
    }

    private fun assertValid(
        clusterTags: List<Tag>,
        tagConfigs: Map<Tag, TopicConfigMap>,
    ) {
        assertThat(check(clusterTags, tagConfigs)).`as`("Should be valid").isNull()
    }

    private fun assertInvalid(
        clusterTags: List<Tag>,
        tagConfigs: Map<Tag, TopicConfigMap>,
    ) {
        assertThat(check(clusterTags, tagConfigs)).`as`("Should be invalid").isNotNull
    }

    private fun check(
        clusterTags: List<Tag>,
        tagConfigs: Map<Tag, TopicConfigMap>,
    ): RuleViolation? {
        val clusterRef = ClusterRef("identifier", clusterTags)
        val topic = newTopic(perTagConfigOverrides = tagConfigs)
        return rule.check(
            topicDescriptionView = TopicDescriptionView(
                name = topic.name,
                properties = topic.properties,
                config = emptyMap(),
                presentOnCluster = true,
                originalDescription = topic,
                existingTopicInfo = null,
            ),
            clusterMetadata = ClusterMetadata(
                clusterRef, newClusterInfo(identifier = clusterRef.identifier)
            ),
        )
    }

    private fun fixConfig(
        clusterTags: List<Tag>,
        tagConfigs: Map<Tag, TopicConfigMap>,
    ): TopicDescription {
        val clusterRef = ClusterRef("identifier", clusterTags)
        return rule.fixConfig(
            topicDescription = newTopic(perTagConfigOverrides = tagConfigs),
            clusterMetadata = ClusterMetadata(
                clusterRef, newClusterInfo(identifier = clusterRef.identifier)
            ),
            existingTopicInfo = null,
        )
    }


}