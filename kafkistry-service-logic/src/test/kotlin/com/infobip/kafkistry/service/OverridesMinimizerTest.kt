package com.infobip.kafkistry.service

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicProperties
import com.infobip.kafkistry.service.generator.OverridesMinimizer
import com.infobip.kafkistry.service.topic.configForCluster
import com.infobip.kafkistry.service.topic.propertiesForCluster
import org.junit.jupiter.api.Test
import java.util.*
import java.util.stream.Collectors

class OverridesMinimizerTest {

    private val minimizer = OverridesMinimizer()
    private val clustersABCD = clustersOf("a", "b", "c", "d")
    private val clustersAB = clustersOf("a", "b")
    private val noClusters = listOf<ClusterRef>()

    private fun clustersOf(vararg identifiers: String) = identifiers.map { ClusterRef(it, emptyList()) }

    @Test
    fun `no clusters`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789")
        )
        val minimized = minimizer.minimizeOverrides(original, noClusters)
        noClusters.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `no overrides nor config`() {
        val original = newTopic(
                properties = TopicProperties(1, 1),
                config = mapOf()
        )
        val minimized = minimizer.minimizeOverrides(original, clustersAB)
        clustersAB.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `only base data`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789")
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `only one properties override`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf("a" to TopicProperties(12, 2))
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `only one config override`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789"),
                perClusterConfigOverrides = mapOf("b" to mapOf("retention.ms" to "555000666"))
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `all the same properties override`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "a" to TopicProperties(12, 2),
                        "b" to TopicProperties(12, 2),
                        "c" to TopicProperties(12, 2),
                        "d" to TopicProperties(12, 2)
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
    }

    @Test
    fun `all the same properties override except one`() {
        val original = newTopic(
                properties = TopicProperties(2, 3),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "a" to TopicProperties(12, 2),
                        "b" to TopicProperties(12, 2),
                        "c" to TopicProperties(12, 2)
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
    }

    @Test
    fun `one properties override`() {
        val original = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "a" to TopicProperties(1, 1)
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersAB)
        clustersAB.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `one properties override second`() {
        val original = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "b" to TopicProperties(1, 1)
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersAB)
        clustersAB.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `two different properties override second`() {
        val original = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "a" to TopicProperties(3, 3),
                        "b" to TopicProperties(1, 1)
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersAB)
        clustersAB.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertThat(minimized.properties).isEqualTo(TopicProperties(3, 3))
    }

    @Test
    fun `remove unknown cluster overrides`() {
        val original = newTopic(
                properties = TopicProperties(2, 2),
                config = mapOf("retention.ms" to "123456789"),
                perClusterProperties = mapOf(
                        "a" to TopicProperties(3, 3),
                        "e" to TopicProperties(1, 1)
                ),
                perClusterConfigOverrides = mapOf(
                        "b" to mapOf("min.insync.replicas" to "2"),
                        "f" to mapOf("min.insync.replicas" to "3"),
                        "g" to mapOf("min.insync.replicas" to "4"),
                        "c" to mapOf("min.insync.replicas" to "5")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersAB.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
    }

    @Test
    fun `only one cluster config override`() {
        val original = newTopic(
                config = mapOf("retention.ms" to "123456789"),
                perClusterConfigOverrides = mapOf(
                        "b" to mapOf("retention.ms" to "6666")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
    }

    @Test
    fun `multiple clusters config same override`() {
        val original = newTopic(
                config = mapOf("retention.ms" to "123456789"),
                perClusterConfigOverrides = mapOf(
                        "b" to mapOf("retention.ms" to "6666"),
                        "c" to mapOf("retention.ms" to "6666"),
                        "d" to mapOf("retention.ms" to "6666")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertThat(minimized.config).isEqualTo(mapOf("retention.ms" to "6666"))
    }

    @Test
    fun `all clusters config different override`() {
        val original = newTopic(
                config = mapOf("retention.ms" to "123456789"),
                perClusterConfigOverrides = mapOf(
                        "a" to mapOf("retention.ms" to "1"),
                        "b" to mapOf("retention.ms" to "2"),
                        "c" to mapOf("retention.ms" to "3"),
                        "d" to mapOf("retention.ms" to "4")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertThat(minimized.config).isEqualTo(mapOf("retention.ms" to "1"))
    }

    @Test
    fun `all clusters config different override keep current`() {
        val original = newTopic(
                config = mapOf("retention.ms" to "123456789"),
                perClusterConfigOverrides = mapOf(
                        "a" to mapOf("retention.ms" to "1"),
                        "b" to mapOf("retention.ms" to "2"),
                        "c" to mapOf("retention.ms" to "3"),
                        "d" to mapOf("retention.ms" to "123456789")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertThat(minimized.config).isEqualTo(mapOf("retention.ms" to "123456789"))
    }

    @Test
    fun `all clusters config sparse overrides`() {
        val original = newTopic(
                config = mapOf(),
                perClusterConfigOverrides = mapOf(
                        "a" to mapOf("retention.ms" to "1000"),
                        "b" to mapOf("retention.bytes" to "1024"),
                        "c" to mapOf("min.insync.replicas" to "2"),
                        "d" to mapOf("message.format" to "2.1")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, false)
        assertThat(minimized.config).isEmpty()
    }

    @Test
    fun `only common configs can bew as base config`() {
        val original = newTopic(
                config = mapOf(),
                perClusterConfigOverrides = mapOf(
                        "a" to mapOf("x" to "1", "y" to "a"),
                        "b" to mapOf("x" to "1", "y" to "a"),
                        "c" to mapOf("x" to "1", "y" to "b"),
                        "d" to mapOf("y" to "a")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertThat(minimized.config).isEqualTo(mapOf("y" to "a"))
    }

    @Test
    fun `remove redundant overrides`() {
        val original = newTopic(
                config = mapOf("x" to "1", "y" to "a", "z" to "-"),
                perClusterConfigOverrides = mapOf(
                        "a" to mapOf("x" to "1", "y" to "a"),
                        "b" to mapOf("x" to "1", "y" to "a", "z" to "-"),
                        "c" to mapOf("x" to "1", "y" to "b"),
                        "d" to mapOf("y" to "a")
                )
        )
        val minimized = minimizer.minimizeOverrides(original, clustersABCD)
        clustersABCD.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertAll {
            assertThat(minimized.config).isEqualTo(original.config)
            assertThat(minimized.perClusterConfigOverrides).isEqualTo(mapOf("c" to mapOf("y" to "b")))
        }
    }

    private val taggedClusters = listOf(
        ClusterRef("a", tags = listOf("large", "foo")),
        ClusterRef("b", tags = listOf("large", "bar")),
        ClusterRef("c", tags = listOf("small", "foo")),
        ClusterRef("d", tags = listOf("small", "bar")),
        ClusterRef("e", tags = listOf("small", "foo")),
    )

    @Test
    fun `extract common tagged properties overrides`() {
        val original = newTopic(
            properties = TopicProperties(1, 1),
            perClusterProperties = mapOf(
                "a" to TopicProperties(50,4),
                "b" to TopicProperties(50,4),
                "c" to TopicProperties(10,3),
            )
        )
        val minimized = minimizer.minimizeOverrides(original, taggedClusters)
        taggedClusters.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertAll {
            assertThat(minimized.properties).isEqualTo(original.properties)
            assertThat(minimized.perTagProperties).isEqualTo(mapOf("large" to TopicProperties(50, 4)))
            assertThat(minimized.perClusterProperties).isEqualTo(mapOf("c" to TopicProperties(10, 3)))
        }
    }

    @Test
    fun `extract common tagged config overrides`() {
        val original = newTopic(
            config = mapOf("x" to "1", "y" to "a", "z" to "-"),
            perClusterConfigOverrides = mapOf(
                "a" to mapOf("x" to "100", "y" to "a"),
                "b" to mapOf("x" to "100", "y" to "a", "z" to "-", "tagged" to "tv"),
                "c" to mapOf("x" to "1", "y" to "b"),
                "d" to mapOf("y" to "a", "tagged" to "tv")
            )
        )
        val minimized = minimizer.minimizeOverrides(original, taggedClusters)
        taggedClusters.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertAll {
            assertThat(minimized.config).isEqualTo(original.config)
            assertThat(minimized.perTagConfigOverrides).isEqualTo(
                mapOf("large" to mapOf("x" to "100"), "bar" to mapOf("tagged" to "tv"))
            )
            assertThat(minimized.perClusterConfigOverrides).isEqualTo(mapOf("c" to mapOf("y" to "b")))
        }
    }

    private val taggedClusters2 = listOf(
        ClusterRef("a", tags = listOf("large", "foo")),
        ClusterRef("b", tags = listOf("large", "bar")),
        ClusterRef("c", tags = listOf("small", "foo")),
        ClusterRef("d", tags = listOf("small", "bar")),
        ClusterRef("e", tags = listOf("small", "foo")),
        ClusterRef("f", tags = listOf("other")),
        ClusterRef("g", tags = listOf("other")),
        ClusterRef("h", tags = listOf("other")),
        ClusterRef("i", tags = listOf("other")),
    )

    @Test
    fun `extract tag property override even when others have same props`() {
        val original = newTopic(
            properties = TopicProperties(1, 3),
            perClusterProperties = mapOf(
                "a" to TopicProperties(2, 4),
                "b" to TopicProperties(2, 4),
                "c" to TopicProperties(2, 4),
                "d" to TopicProperties(1, 3),
            )
        )
        val minimized = minimizer.minimizeOverrides(original, taggedClusters2)
        taggedClusters.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertAll {
            assertThat(minimized.properties).isEqualTo(TopicProperties(1, 3))
            assertThat(minimized.perTagProperties).isEqualTo(
                mapOf("large" to TopicProperties(2, 4))
            )
            assertThat(minimized.perClusterProperties).isEqualTo(
                mapOf("c" to TopicProperties(2, 4))
            )
        }
    }

    @Test
    fun `extract tag configs override even when others have same configs`() {
        val original = newTopic(
            perClusterConfigOverrides = mapOf(
                "a" to mapOf("x" to "x1", "y" to "l", "z" to "p"),
                "b" to mapOf("x" to "x1", "y" to "l"),
                "c" to mapOf("x" to "x1", "y" to "s", "z" to "p"),
                "d" to mapOf("x" to "x1", "y" to "s"),
                "e" to mapOf("x" to "x1", "y" to "s", "z" to "p"),
                "f" to mapOf("x" to "x1", "y" to "o"),
                "g" to mapOf("x" to "x1", "y" to "o"),
                "h" to mapOf("x" to "x1", "y" to "o"),
                "i" to mapOf("x" to "x1", "y" to "o", "z" to "p"),
            )
        )
        val minimized = minimizer.minimizeOverrides(original, taggedClusters2)
        taggedClusters.assertThatAllEffectiveConfigurationIsSame(original, minimized, true)
        assertAll {
            assertThat(minimized.config).isEqualTo(
                mapOf(
                    "x" to "x1", "y" to "o",
                )
            )
            assertThat(minimized.perTagConfigOverrides).isEqualTo(
                mapOf(
                    "large" to mapOf("y" to "l"),
                    "small" to mapOf("y" to "s"),
                    "foo" to mapOf("z" to "p"),
                )
            )
            assertThat(minimized.perClusterConfigOverrides).isEqualTo(
                mapOf("i" to mapOf("z" to "p"))
            )
        }
    }

    @Test
    fun `randomly generated topic to satisfy minimization properties`() {
        val seed = System.currentTimeMillis()
        val random = Random(seed)
        val numRuns = 10_000
        val stats = MinimizeStats()
        repeat(numRuns) { run ->
            val allTags = random.range(0..5).map { "tag_$it" }
            val allClusters = random.range(1..15)
                .map {
                    ClusterRef("c_$it", tags = allTags.filter { random.nextBoolean() })
                }
            val original = random.newTopic(allClusters)
            val minimizeRun = original.minimize(allClusters)
            stats.observe(minimizeRun)
            try {
                minimizeRun.assertThatAllEffectiveConfigurationIsSame(requireBetterOrEqualScoreForAll = false)
            } catch (e: Error) {
                println("Failure for seed: $seed on run $run")
                println(" -clusters: $allClusters")
                println(" -original: $original")
                println(" -minimized: ${minimizeRun.minimized}")
                throw e
            }
        }
        stats.printStats()
        stats.assertStatisticallyGood()
    }

    private fun Random.newTopic(allClusters: List<ClusterRef>): TopicDescription {
        return newTopic(
            properties = TopicProperties(between(2..4), between(1..3)),
            perClusterProperties = allClusters
                .filter { nextBoolean() }
                .associate { clusterRef ->
                    clusterRef.identifier to TopicProperties(between(2..4), between(1..3))
                },
            config = stringStringMap(5, 4),
            perClusterConfigOverrides = allClusters
                .filter { nextBoolean() }
                .associate { clusterRef ->
                    clusterRef.identifier to stringStringMap(6, 4)
                }
        )
    }

    private fun List<ClusterRef>.assertThatAllEffectiveConfigurationIsSame(
            original: TopicDescription, minimized: TopicDescription, shouldChange: Boolean?
    ) {
        MinimizeRun(this, original, minimized, original.score(), minimized.score())
            .assertThatAllEffectiveConfigurationIsSame(shouldChange)
    }

    private fun MinimizeRun.assertThatAllEffectiveConfigurationIsSame(
        shouldChange: Boolean? = null,
        requireBetterOrEqualScoreForAll: Boolean = true,
    ) {
        val identifiers = allClusters.map { it.identifier }
        assertAll {
            allClusters.forEach { clusterRef ->
                val originalProperties = original.propertiesForCluster(clusterRef)
                val minimizedProperties = minimized.propertiesForCluster(clusterRef)
                val originalConfig = original.configForCluster(clusterRef)
                val minimizedConfig = minimized.configForCluster(clusterRef)
                val propertiesOverrideClusters = minimized.perClusterProperties.keys.toList()
                val configsOverrideClusters = minimized.perClusterProperties.keys.toList()
                assertThat(minimizedProperties)
                    .`as`("for cluster '${clusterRef.identifier}' properties are equal")
                    .isEqualTo(originalProperties)
                assertThat(minimizedConfig)
                    .`as`("for cluster '${clusterRef.identifier}' config is equal")
                    .isEqualTo(originalConfig)
                assertThat(propertiesOverrideClusters).`as`("has only known clusters")
                        .allMatch { it in identifiers }
                assertThat(configsOverrideClusters).`as`("has only known clusters")
                        .allMatch { it in identifiers }
                if (shouldChange != null) {
                    if (shouldChange) {
                        assertThat(minimized).`as`("should be changed").isNotEqualTo(original)
                    } else {
                        assertThat(minimized).`as`("should not be changed").isEqualTo(original)
                    }
                }
                //assert that minimization took effect
                if (requireBetterOrEqualScoreForAll) {
                    assertThat(newScore.weight)
                        .`as`("new score weight compared to old weight")
                        .isLessThanOrEqualTo(oldScore.weight)
                    assertThat(minimized.perClusterProperties.size)
                        .`as`("less or equal number of properties overrides after")
                        .isLessThanOrEqualTo(original.perClusterProperties.size)
                    assertThat(minimized.perClusterConfigOverrides.size)
                        .`as`("less or equal number of config overrides after")
                        .isLessThanOrEqualTo(original.perClusterConfigOverrides.size)
                }
            }
        }
    }

    private fun Random.stringStringMap(maxKeys: Int, valuesCardinality: Int): Map<String, String> {
        return range(0..maxKeys)
                .filter { nextBoolean() }
                .associate { "k_$it" to "${nextInt(valuesCardinality)}" }
    }

    private fun Random.between(range: IntRange): Int {
        return nextInt(range.last - range.first) + range.first
    }

    private fun Random.range(lengthRange: IntRange): List<Int> {
        return (1..between(lengthRange)).map { it }
    }

    private fun TopicDescription.minimize(allClusters: List<ClusterRef>): MinimizeRun {
        val minimized = minimizer.minimizeOverrides(this, allClusters)
        return MinimizeRun(
            allClusters = allClusters,
            original = this,
            minimized = minimized,
            oldScore = this.score(),
            newScore = minimized.score(),
        )
    }
    private class MinimizeStats {

        private var runs = 0
        private var numChanged = 0
        private var numMinimized = 0
        private var numWorsened = 0
        private var numTagsForPropsUsed = 0
        private var numTagsForConfigUsed = 0
        private val scores = mutableListOf<Pair<Score, Score>>()

        fun observe(minimizeRun: MinimizeRun) {
            runs++
            with(minimizeRun) {
                if (minimized != original) numChanged++
                if (minimized.perTagProperties.isNotEmpty()) numTagsForPropsUsed++
                if (minimized.perTagConfigOverrides.isNotEmpty()) numTagsForConfigUsed++
                if (newScore.weight < oldScore.weight) numMinimized++
                if (newScore.weight > oldScore.weight) numWorsened++
                scores.add(oldScore to newScore)
            }
        }

        fun printStats() {
            println("---------------------------")
            println("----- MINIMIZE STATS ------")
            println("Total runs:    $runs")
            println("Num changed:   $numChanged (${100.0 * numChanged / runs}%)")
            println("Num minimized: $numMinimized (${100.0 * numMinimized / runs}%)")
            println("Num worsened:  $numWorsened (${100.0 * numWorsened / runs}%)")
            println("Tag prop used: $numTagsForPropsUsed (${100.0 * numTagsForPropsUsed / runs}%)")
            println("Tag conf used: $numTagsForConfigUsed (${100.0 * numTagsForConfigUsed / runs}%)")

            fun Pair<Score, Score>.format(): String {
                val (old, new) = this
                return "%4d %4d %4d -> %4d %4d %4d | %4d %4d %4d".format(
                    old.configsCount, old.overrideCount, old.weight,
                    new.configsCount, new.overrideCount, new.weight,
                    old.configsCount-new.configsCount, old.overrideCount-new.overrideCount, old.weight-new.weight,
                )
            }
            val sortedScores = scores.sortedByDescending { (old, new) -> old.weight - new.weight }
            println("Best changes:")
            println("  O.cc O.oc  O.w -> N.cc N.oc N.w  | d.cc d.oc  d.w")
            sortedScores.take(10).forEach { println("  "+ it.format()) }
            println("Worst changes:")
            println("  O.cc O.oc  O.w -> N.cc N.oc N.w  | d.cc d.oc  d.w")
            sortedScores.reversed().take(10).forEach { println("  "+ it.format()) }
            scores.stream()
                .collect(Collectors.summarizingInt { it.first.weight })
                .also { println("Old weight stats: $it") }
            scores.stream()
                .collect(Collectors.summarizingInt { it.second.weight })
                .also { println("New weight stats: $it") }
            scores.stream()
                .collect(Collectors.summarizingInt { it.first.weight - it.second.weight })
                .also { println("Old weight - new weight stats: $it") }
            println("---------------------------")
        }

        fun assertStatisticallyGood() {
            assertAll {
                assertThat(numWorsened).`as`("num worsened < 1% of $runs").isLessThan(runs / 100)
                assertThat(numMinimized).`as`("num minimized > 50% of $runs").isGreaterThan(runs / 2)
            }
        }
    }
}

data class MinimizeRun(
    val allClusters: List<ClusterRef>,
    val original: TopicDescription,
    val minimized: TopicDescription,
    val oldScore: Score,
    val newScore: Score,
)

data class Score(
    val configsCount: Int,
    val overrideCount: Int,
) {
    val weight = configsCount + overrideCount
}

fun TopicDescription.score(): Score {
    val configs = config.size +
            perClusterConfigOverrides.entries.sumOf { it.value.size } +
            perTagConfigOverrides.entries.sumOf { it.value.size }
    val clusterOverrides = (perClusterConfigOverrides.keys +perClusterProperties.keys).size
    val tagOverrides = (perTagConfigOverrides.keys + perTagProperties.keys).size
    return Score(
        configsCount = configs,
        overrideCount = clusterOverrides + tagOverrides,
    )
}
