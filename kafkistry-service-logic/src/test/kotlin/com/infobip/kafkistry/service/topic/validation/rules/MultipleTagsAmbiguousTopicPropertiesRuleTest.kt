package com.infobip.kafkistry.service.topic.validation.rules

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.service.newClusterInfo
import com.infobip.kafkistry.service.newTopic
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.RuleViolation
import org.junit.Test

@Suppress("PrivatePropertyName")
class MultipleTagsAmbiguousTopicPropertiesRuleTest {

    private val rule = MultipleTagsAmbiguousTopicPropertiesRule()
    private val PROPS = TopicProperties(1, 1)
    private val PROPS_1 = TopicProperties(2, 1)
    private val PROPS_2 = TopicProperties(6, 3)
    private val PROPS_3 = TopicProperties(12, 3)

    @Test
    fun `no tags valid`() = assertValid(emptyList(), PROPS, emptyMap())

    @Test
    fun `single valid`() = assertValid(listOf("A"), PROPS, mapOf("A" to PROPS_1))

    @Test
    fun `no conflicts 1 valid`() = assertValid(listOf("A"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_2))

    @Test
    fun `no conflicts 2 valid`() = assertValid(listOf("A", "B"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_1))

    @Test
    fun `conflicts 1 invalid`() = assertInvalid(listOf("A", "B"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_2))

    @Test
    fun `conflicts 1 fix`() {
        val topicDescription = fixConfig(listOf("A", "B"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_2))
        assertThat(topicDescription).isEqualTo(
            newTopic(
            properties = PROPS,
            perTagProperties = mapOf("A" to PROPS_1),
        )
        )
    }

    @Test
    fun `conflicts 2 invalid`() = assertInvalid(
        listOf("A", "B", "E"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_2, "C" to PROPS_3, "D" to PROPS_2, "E" to PROPS_1)
    )

    @Test
    fun `conflicts 2 fix`() {
        val topicDescription = fixConfig(
            listOf("A", "B", "E"), PROPS, mapOf("A" to PROPS_1, "B" to PROPS_2, "C" to PROPS_3, "D" to PROPS_2, "E" to PROPS_1)
        )
        assertThat(topicDescription).isEqualTo(
            newTopic(
            properties = PROPS,
            perTagProperties = mapOf("A" to PROPS_1, "C" to PROPS_3, "D" to PROPS_2, "E" to PROPS_1),
        )
        )
    }

    private fun assertValid(
        clusterTags: List<Tag>,
        globalProperties: TopicProperties,
        tagProperties: Map<Tag, TopicProperties>
    ) {
        assertThat(check(clusterTags, globalProperties, tagProperties)).`as`("Should be valid").isNull()
    }

    private fun assertInvalid(
        clusterTags: List<Tag>,
        globalProperties: TopicProperties,
        tagProperties: Map<Tag, TopicProperties>
    ) {
        assertThat(check(clusterTags, globalProperties, tagProperties)).`as`("Should be invalid").isNotNull
    }

    private fun check(
        clusterTags: List<Tag>,
        globalProperties: TopicProperties,
        tagProperties: Map<Tag, TopicProperties>,
    ): RuleViolation? {
        val clusterRef = ClusterRef("identifier", clusterTags)
        return rule.check(
            topicDescriptionView = TopicDescriptionView(
                name = "",
                properties = globalProperties,
                config = emptyMap(),
                presentOnCluster = true,
                newTopic(
                    properties = globalProperties,
                    perTagProperties = tagProperties,
                ),
                existingTopicInfo = null,
            ),
            clusterMetadata = ClusterMetadata(
                clusterRef, newClusterInfo(identifier = clusterRef.identifier)
            ),
        )
    }

    private fun fixConfig(
        clusterTags: List<Tag>,
        globalProperties: TopicProperties,
        tagProperties: Map<Tag, TopicProperties>
    ): TopicDescription {
        val clusterRef = ClusterRef("identifier", clusterTags)
        return rule.fixConfig(
            topicDescription = newTopic(
                properties = globalProperties,
                perTagProperties = tagProperties,
            ),
            clusterMetadata = ClusterMetadata(
                clusterRef, newClusterInfo(identifier = clusterRef.identifier)
            ),
            existingTopicInfo = null,
        )
    }

}