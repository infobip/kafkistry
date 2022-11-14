package com.infobip.kafkistry.autopilot.enabled

import com.infobip.kafkistry.autopilot.binding.*
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties.StringValues
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.Tag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

internal class AutopilotEnabledFilterTest {

    private val binding = TestBinding()
    private val bindingA = TestBindingA()
    private val bindingB = TestBindingB()
    private val actionA = TestActionA()
    private val actionB = TestActionB()

    @Nested
    inner class GlobalTest {

        @Test
        fun `everything disabled`() {
            val filter = newFilter { enabled = false }
            assertAll(
                { filter.assertEnabled(binding, actionA).isFalse() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isFalse() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }

        @Test
        fun `everything enabled`() {
            val filter = newFilter { enabled = true }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isTrue() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isTrue() },
            )
        }
    }

    @Nested
    inner class BindingFilterTest {

        @Test
        fun `only specific binding enabled`() {
            val filter = newFilter {
                bindingType.enabledClasses = setOf(TestBindingA::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isFalse() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }

        @Test
        fun `only specific binding disabled`() {
            val filter = newFilter {
                bindingType.disabledClasses = setOf(TestBindingA::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isTrue() },
                { filter.assertEnabled(bindingA, actionA).isFalse() },
                { filter.assertEnabled(bindingB, actionB).isTrue() },
            )
        }

        @Test
        fun `base binding enabled`() {
            val filter = newFilter {
                bindingType.enabledClasses = setOf(BaseTestBinding::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isTrue() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isTrue() },
            )
        }

        @Test
        fun `base binding disabled`() {
            val filter = newFilter {
                bindingType.disabledClasses = setOf(BaseTestBinding::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isFalse() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isFalse() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }
    }

    @Nested
    inner class ActionTypeTest {

        @Test
        fun `only specific action enabled`() {
            val filter = newFilter {
                actionType.enabledClasses = setOf(TestActionA::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }

        @Test
        fun `only specific action disabled`() {
            val filter = newFilter {
                actionType.disabledClasses = setOf(TestActionA::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isFalse() },
                { filter.assertEnabled(binding, actionB).isTrue() },
                { filter.assertEnabled(bindingA, actionA).isFalse() },
                { filter.assertEnabled(bindingB, actionB).isTrue() },
            )
        }

        @Test
        fun `base action enabled`() {
            val filter = newFilter {
                actionType.enabledClasses = setOf(BaseTestAction::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isTrue() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isTrue() },
            )
        }

        @Test
        fun `base action disabled`() {
            val filter = newFilter {
                actionType.disabledClasses = setOf(BaseTestAction::class.java)
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isFalse() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isFalse() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }
    }

    @Nested
    inner class TargetTypeTest {

        @Test
        fun `specific target type enabled`() {
            val filter = newFilter {
                targetType.enabledValues = setOf("TEST_A")
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }

        @Test
        fun `specific target type disabled`() {
            val filter = newFilter {
                targetType.disabledValues = setOf("TEST_B")
            }
            assertAll(
                { filter.assertEnabled(binding, actionA).isTrue() },
                { filter.assertEnabled(binding, actionB).isFalse() },
                { filter.assertEnabled(bindingA, actionA).isTrue() },
                { filter.assertEnabled(bindingB, actionB).isFalse() },
            )
        }

    }

    @Nested
    inner class AttributeFilterTest {

        private val actionA1x = TestActionAttr("a1" to "x")
        private val actionA1y = TestActionAttr("a1" to "y")
        private val actionA2x = TestActionAttr("a2" to "x")
        private val actionA2y = TestActionAttr("a2" to "y")

        @Test
        fun `specific attribute type enabled`() {
            val filter = newFilter {
                attribute = mapOf("a1" to StringValues().apply { enabledValues = setOf("x") })
            }
            assertAll(
                { filter.assertEnabled(binding, actionA1x).isTrue() },
                { filter.assertEnabled(binding, actionA1y).isFalse() },
                { filter.assertEnabled(binding, actionA2x).isTrue() },
                { filter.assertEnabled(binding, actionA2y).isTrue() },
            )
        }

        @Test
        fun `specific attribute type disabled`() {
            val filter = newFilter {
                attribute = mapOf("a1" to StringValues().apply { disabledValues = setOf("x") })
            }
            assertAll(
                { filter.assertEnabled(binding, actionA1x).isFalse() },
                { filter.assertEnabled(binding, actionA1y).isTrue() },
                { filter.assertEnabled(binding, actionA2x).isTrue() },
                { filter.assertEnabled(binding, actionA2y).isTrue() },
            )
        }

    }

    @Nested
    inner class ClusterFilterTest {

        private val actionNoCluster = TestActionAttr()
        private val actionC1ProdF1 = TestActionCluster("c1", listOf("prod", "feature1"))
        private val actionC2ProdF2 = TestActionCluster("c2", listOf("prod", "feature2"))
        private val actionC3TestF1 = TestActionCluster("c3", listOf("test", "feature1"))
        private val actionC4TestF2 = TestActionCluster("c4", listOf("test", "feature2"))

        @Test
        fun `enabled identifiers`() {
            val filter = newFilter { cluster.identifiers.included = setOf("c1", "c4") }
            assertAll(
                { filter.assertEnabled(binding, actionNoCluster).isTrue() },
                { filter.assertEnabled(binding, actionC1ProdF1).isTrue() },
                { filter.assertEnabled(binding, actionC2ProdF2).isFalse() },
                { filter.assertEnabled(binding, actionC3TestF1).isFalse() },
                { filter.assertEnabled(binding, actionC4TestF2).isTrue() },
            )
        }

        @Test
        fun `disabled identifiers`() {
            val filter = newFilter { cluster.identifiers.excluded = setOf("c1", "c4") }
            assertAll(
                { filter.assertEnabled(binding, actionNoCluster).isTrue() },
                { filter.assertEnabled(binding, actionC1ProdF1).isFalse() },
                { filter.assertEnabled(binding, actionC2ProdF2).isTrue() },
                { filter.assertEnabled(binding, actionC3TestF1).isTrue() },
                { filter.assertEnabled(binding, actionC4TestF2).isFalse() },
            )
        }

        @Test
        fun `enabled tag`() {
            val filter = newFilter { cluster.tags.included = setOf("prod") }
            assertAll(
                { filter.assertEnabled(binding, actionNoCluster).isTrue() },
                { filter.assertEnabled(binding, actionC1ProdF1).isTrue() },
                { filter.assertEnabled(binding, actionC2ProdF2).isTrue() },
                { filter.assertEnabled(binding, actionC3TestF1).isFalse() },
                { filter.assertEnabled(binding, actionC4TestF2).isFalse() },
            )
        }

        @Test
        fun `disabled tag`() {
            val filter = newFilter { cluster.tags.excluded = setOf("prod") }
            assertAll(
                { filter.assertEnabled(binding, actionNoCluster).isTrue() },
                { filter.assertEnabled(binding, actionC1ProdF1).isFalse() },
                { filter.assertEnabled(binding, actionC2ProdF2).isFalse() },
                { filter.assertEnabled(binding, actionC3TestF1).isTrue() },
                { filter.assertEnabled(binding, actionC4TestF2).isTrue() },
            )
        }

    }

    private fun newFilter(
        configurer: AutopilotEnabledFilterProperties.() -> Unit = {}
    ): AutopilotEnabledFilter {
        val properties = AutopilotEnabledFilterProperties()
            .apply { enabled = true }
            .apply(configurer)
        return AutopilotEnabledFilter(properties)
    }

    private fun <A : AutopilotAction> AutopilotEnabledFilter.assertEnabled(
        binding: AutopilotBinding<A>, action: A,
    ) = assertThat(isEnabled(binding, action))
        .`as`("Binding=${binding.javaClass.simpleName} Action=${action.actionIdentifier}")


    abstract class BaseTestAction(
        targetType: ActionTargetType = "TEST",
        clusterRef: ClusterRef? = null,
        attributes: Map<String, String> = emptyMap(),
    ) : AutopilotAction {
        override val metadata = ActionMetadata(
            actionIdentifier = javaClass.simpleName, clusterRef = clusterRef, attributes = attributes,
            description = ActionDescription(
                actionName = javaClass.simpleName,
                actionClass = javaClass.name,
                targetType = targetType,
                doc = "",
            ),
        )
    }

    class TestActionA : BaseTestAction("TEST_A")
    class TestActionB : BaseTestAction("TEST_B")
    class TestActionAttr(vararg attributes: Pair<String, String>): BaseTestAction(attributes = attributes.toMap())
    class TestActionCluster(
        clusterIdentifier: KafkaClusterIdentifier, tags: List<Tag>
    ): BaseTestAction(clusterRef = ClusterRef(clusterIdentifier, tags))

    abstract class BaseTestBinding<A : BaseTestAction> : AutopilotBinding<A> {
        override fun listCapabilities(): List<ActionDescription> = emptyList()
        override fun actionsToProcess(): List<A> = emptyList()
        override fun processAction(action: A) = Unit
        override fun checkBlockers(action: A): List<AutopilotActionBlocker> = emptyList()
    }

    class TestBinding : BaseTestBinding<BaseTestAction>()
    class TestBindingA : BaseTestBinding<TestActionA>()
    class TestBindingB : BaseTestBinding<TestActionB>()

}