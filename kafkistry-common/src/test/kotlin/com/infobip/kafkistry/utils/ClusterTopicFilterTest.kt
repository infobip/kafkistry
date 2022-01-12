package com.infobip.kafkistry.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles

/**
 * Properties: [application-cluster-topic-filter-it.yaml](classpath://application-cluster-topic-filter-it.yaml)
 */
@Configuration
@EnableAutoConfiguration
class ClusterTopicFilterConfig {

    @Bean
    @ConfigurationProperties("test.feature1")
    fun feature1() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature2")
    fun feature2() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature3")
    fun feature3() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature4")
    fun feature4() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature5")
    fun feature5() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature6")
    fun feature6() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature7")
    fun feature7() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature8")
    fun feature8() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature9")
    fun feature9() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature10")
    fun feature10() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature11")
    fun feature11() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature12")
    fun feature12() = FeatureProps()

    @Bean
    @ConfigurationProperties("test.feature13")
    fun feature13() = FeatureProps()
}

class FeatureProps {
    var enabledOn = ClusterTopicFilterProperties()
}

@SpringBootTest(
    classes = [ClusterTopicFilterConfig::class],
)
@ActiveProfiles("cluster-topic-filter-it")
class ClusterTopicFilterTest {

    @Autowired
    lateinit var config: ClusterTopicFilterConfig

    @Test
    fun `feature1 - nothing specified`() {
        ClusterTopicFilter(config.feature1().enabledOn).assertFilter(
            c1t1 = true, c1t2 = true,
            c2t1 = true, c2t2 = true,
        )
    }

    @Test
    fun `feature2 - c1 included - inline`() {
        ClusterTopicFilter(config.feature2().enabledOn).assertFilter(
            c1t1 = true, c1t2 = true,
            c2t1 = false, c2t2 = false,
        )
    }

    @Test
    fun `feature3 - c1 included - list`() {
        ClusterTopicFilter(config.feature2().enabledOn).assertFilter(
            c1t1 = true, c1t2 = true,
            c2t1 = false, c2t2 = false,
        )
    }

    @Test
    fun `feature4 - all included star inline`() {
        ClusterTopicFilter(config.feature4().enabledOn).assertFilter(
            c1t1 = true, c1t2 = true,
            c2t1 = true, c2t2 = true,
        )
    }

    @Test
    fun `feature5 - all excluded star inline`() {
        ClusterTopicFilter(config.feature5().enabledOn).assertFilter(
            c1t1 = false, c1t2 = false,
            c2t1 = false, c2t2 = false,
        )
    }

    @Test
    fun `feature6 - excluded t1 inline`() {
        ClusterTopicFilter(config.feature6().enabledOn).assertFilter(
            c1t1 = false, c1t2 = true,
            c2t1 = false, c2t2 = true,
        )
    }

    @Test
    fun `feature7 - excluded t2 and t3 list`() {
        ClusterTopicFilter(config.feature7().enabledOn).assertFilter(
            c1t1 = true, c1t2 = false,
            c2t1 = true, c2t2 = false,
        )
    }

    @Test
    fun `feature8 - included t1 inline`() {
        ClusterTopicFilter(config.feature8().enabledOn).assertFilter(
            c1t1 = true, c1t2 = false,
            c2t1 = true, c2t2 = false,
        )
    }

    @Test
    fun `feature9 - included t1 and c2`() {
        ClusterTopicFilter(config.feature9().enabledOn).assertFilter(
            c1t1 = false, c1t2 = false,
            c2t1 = true, c2t2 = false,
        )
    }

    @Test
    fun `feature10 - excluded t1 and c2`() {
        ClusterTopicFilter(config.feature10().enabledOn).assertFilter(
            c1t1 = false, c1t2 = true,
            c2t1 = false, c2t2 = false,
        )
    }

    @Test
    fun `feature11 - included t1,t2 and c2,c3 inline`() {
        ClusterTopicFilter(config.feature11().enabledOn).assertFilter(
            c1t1 = false, c1t2 = false,
            c2t1 = true, c2t2 = true,
        )
    }

    @Test
    fun `feature12 - included t2 and (empty) inline`() {
        ClusterTopicFilter(config.feature12().enabledOn).assertFilter(
            c1t1 = false, c1t2 = true,
            c2t1 = false, c2t2 = true,
        )
    }

    @Test
    fun `feature13 - excluded t2 and (empty) inline`() {
        ClusterTopicFilter(config.feature13().enabledOn).assertFilter(
            c1t1 = true, c1t2 = false,
            c2t1 = true, c2t2 = false,
        )
    }

    private fun ClusterTopicFilter.assertFilter(
        c1t1: Boolean, c1t2: Boolean,
        c2t1: Boolean, c2t2: Boolean,
    ) {
        assertThat(filter("c1", "t1")).`as`("c1 - t1").isEqualTo(c1t1)
        assertThat(filter("c1", "t2")).`as`("c1 - t2").isEqualTo(c1t2)
        assertThat(filter("c2", "t1")).`as`("c2 - t1").isEqualTo(c2t1)
        assertThat(filter("c2", "t2")).`as`("c2 - t2").isEqualTo(c2t2)
    }
}