package com.infobip.kafkistry.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleScriptContext

class TestJSBase {

    companion object {
        init {
            System.setProperty("polyglot.engine.WarnInterpreterOnly", "false")
        }

        val jsEngine: ScriptEngine = ScriptEngineManager().getEngineByName("js")
    }

    private val jsContext: ScriptContext = SimpleScriptContext()

    private fun loadJSFile(path: String) {
        jsEngine.eval(ClassPathResource(path).inputStream.reader(), jsContext)
    }

    private fun setString(name: String, value: String) {
        jsContext.getBindings(ScriptContext.ENGINE_SCOPE)[name] = value
    }

    @BeforeEach
    fun loadScripts() {
        loadJSFile("ui/static/util.js")
    }

    @Nested
    inner class TextContainsJIRAText {

        private fun textContainsJIRA(text: String): Boolean {
            setString("text", text)
            return jsEngine.eval("textContainsJIRA(text);", jsContext) as Boolean
        }

        @Test
        fun `bare JIRA key`() {
            assertThat(textContainsJIRA("KFK-123")).isTrue
        }

        @Test
        fun `no JIRA key`() {
            assertThat(textContainsJIRA("not-a-jira-task")).isFalse
        }

        @Test
        fun `JIRA key preceded by hyphen`() {
            assertThat(textContainsJIRA("feature-KFK-123")).isTrue
        }

        @Test
        fun `JIRA key followed by hyphen`() {
            assertThat(textContainsJIRA("KFK-123-feature")).isTrue
        }

        @Test
        fun `JIRA key surrounded by hyphens`() {
            assertThat(textContainsJIRA("my-branch-KFK-123-description")).isTrue
        }

        @Test
        fun `JIRA key inside parentheses`() {
            assertThat(textContainsJIRA("(KFK-123)")).isTrue
        }

        @Test
        fun `JIRA key inside brackets`() {
            assertThat(textContainsJIRA("[KFK-123]")).isTrue
        }

        @Test
        fun `JIRA key preceded by underscore`() {
            assertThat(textContainsJIRA("MY_KFK-123")).isTrue
        }

        @Test
        fun `JIRA key followed by colon`() {
            assertThat(textContainsJIRA("KFK-123: fix the bug")).isTrue
        }

        @Test
        fun `JIRA key preceded by slash`() {
            assertThat(textContainsJIRA("bug/KFK-123")).isTrue
        }

        @Test
        fun `lowercase JIRA prefix not recognized`() {
            assertThat(textContainsJIRA("kfk-123")).isFalse
        }

        @Test
        fun `no match on number-only prefix`() {
            assertThat(textContainsJIRA("123-456")).isFalse
        }

        @Test
        fun `no match on prefix without number`() {
            assertThat(textContainsJIRA("KFK")).isFalse
        }

    }

    @Nested
    inner class ExtractJiraIssuesTest {

        private fun extractJiraIssues(text: String): List<String> {
            setString("text", text)
            return (jsEngine.eval("extractJiraIssues(text);", jsContext) as List<*>).map { it.toString() }
        }

        @Test
        fun `single JIRA key`() {
            assertThat(extractJiraIssues("KFK-123")).containsExactly("KFK-123")
        }

        @Test
        fun `no JIRA key`() {
            assertThat(extractJiraIssues("not-a-jira")).isEmpty()
        }

        @Test
        fun `multiple keys`() {
            assertThat(extractJiraIssues("KFK-123 CNS-456")).containsExactly("KFK-123", "CNS-456")
        }

        @Test
        fun `key preceded by hyphen`() {
            assertThat(extractJiraIssues("my-branch-KFK-123")).containsExactly("KFK-123")
        }

        @Test
        fun `key followed by hyphen`() {
            assertThat(extractJiraIssues("KFK-123-my-feature")).containsExactly("KFK-123")
        }

        @Test
        fun `key inside parentheses`() {
            assertThat(extractJiraIssues("(KFK-123)")).containsExactly("KFK-123")
        }

        @Test
        fun `key inside brackets`() {
            assertThat(extractJiraIssues("[KFK-123] fix")).containsExactly("KFK-123")
        }

        @Test
        fun `keys separated by comma`() {
            assertThat(extractJiraIssues("KFK-123,CNS-456")).containsExactly("KFK-123", "CNS-456")
        }

        @Test
        fun `keys in running text`() {
            assertThat(extractJiraIssues("fix KFK-123 and CNS-456 done")).containsExactly("KFK-123", "CNS-456")
        }

        @Test
        fun `key followed by question mark`() {
            assertThat(extractJiraIssues("should KFK-123?")).containsExactly("KFK-123")
        }

        @Test
        fun `number-only prefix not extracted`() {
            assertThat(extractJiraIssues("123-456")).isEmpty()
        }

        @Test
        fun `no false positive on lowercase`() {
            assertThat(extractJiraIssues("kfk-123")).isEmpty()
        }

    }

    @Nested
    inner class AppendJiraIssuesIfAnyTest {

        private fun appendJiraIssuesIfAny(reasonMessage: String, description: String): String {
            setString("reasonMessage", reasonMessage)
            setString("description", description)
            return jsEngine.eval("appendJiraIssuesIfAny(reasonMessage, description);", jsContext) as String
        }

        @Test
        fun `no JIRA in description`() {
            assertThat(appendJiraIssuesIfAny("msg", "no jira")).isEqualTo("msg")
        }

        @Test
        fun `single JIRA`() {
            assertThat(appendJiraIssuesIfAny("msg", "KFK-123")).isEqualTo("msg (Jira: KFK-123)")
        }

        @Test
        fun `multiple JIRAs`() {
            assertThat(appendJiraIssuesIfAny("msg", "KFK-123 CNS-456")).isEqualTo("msg (Jira: KFK-123,CNS-456)")
        }

        @Test
        fun `empty description`() {
            assertThat(appendJiraIssuesIfAny("msg", "")).isEqualTo("msg")
        }

    }

}