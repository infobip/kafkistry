package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.model.AclOperation
import com.infobip.kafkistry.model.AclOperation.Policy.ALLOW
import com.infobip.kafkistry.model.AclOperation.Policy.DENY
import com.infobip.kafkistry.model.AclOperation.Type.READ
import com.infobip.kafkistry.model.AclOperation.Type.WRITE
import com.infobip.kafkistry.model.AclResource
import com.infobip.kafkistry.model.AclResource.NamePattern.*
import com.infobip.kafkistry.model.AclResource.Type.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class KafkaAclRuleExtensionsTest {

    private val log = LoggerFactory.getLogger(KafkaAclRuleExtensionsTest::class.java)

    private fun KafkaAclRule.testAsStringAndParse() {
        val asString = asString()
        log.info("Testing rule -> {}", asString)
        val parsedRule = asString.parseAcl()
        assertThat(parsedRule).`as`("Rule as string -> $asString").isEqualTo(this)
    }

    @Test
    fun `simple rule 1`() {
        KafkaAclRule("user", AclResource(TOPIC, "topic", LITERAL), "*", AclOperation(WRITE, ALLOW))
            .testAsStringAndParse()
    }

    @Test
    fun `rule with whitespace in resource`() {
        KafkaAclRule("user", AclResource(GROUP, "two words", LITERAL), "*", AclOperation(READ, ALLOW))
            .testAsStringAndParse()
    }

    @Test
    fun `rule with whitespace in principal`() {
        KafkaAclRule("my user", AclResource(TRANSACTIONAL_ID, "tid", PREFIXED), "10.0.0.1", AclOperation(AclOperation.Type.ALL, DENY))
            .testAsStringAndParse()
    }

    @Test
    fun `rule with whitespace prefixed`() {
        KafkaAclRule("u", AclResource(TOPIC, "pref topic-", PREFIXED), "*", AclOperation(READ, ALLOW))
            .testAsStringAndParse()
    }

    @Test
    fun `rule all topic resources`() {
        KafkaAclRule("u", AclResource(TOPIC, "*", LITERAL), "*", AclOperation(READ, ALLOW))
            .testAsStringAndParse()
    }

    @Test
    fun `rule literal resource with star`() {
        KafkaAclRule("u", AclResource(TOPIC, "topic*", LITERAL), "*", AclOperation(READ, ALLOW))
            .testAsStringAndParse()
    }
}