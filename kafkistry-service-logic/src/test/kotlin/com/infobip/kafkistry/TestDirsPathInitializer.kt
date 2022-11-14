package com.infobip.kafkistry

import com.infobip.kafkistry.utils.test.newTestFolder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

/**
 * Context initializer which adds properties to context for properties which have value that is path
 * to directory to be used.
 *
 * All those paths are generated to be random temporary directories which have JVM shutdown hook
 * to be deleted when JVM is about to stop.
 *
 * It's possible to specify property `EXTRA_TEST_DIR_PROPERTIES` which have comma-separated list of
 * extra property names to generate random temporary dir and inject all those properties into context's environment.
 *
 * For example, specifying:
 * ```
 * EXTRA_TEST_DIR_PROPERTIES=MY_DIR,app.feature.dir
 * ```
 * Could inject something like this into context.
 * ```
 * MY_DIR=/tmp/test-tmp-dir-MY_DIR-45678327645478
 * app.feature.dir=/tmp/test-tmp-dir-app.feature.dir-734734173647495
 * ```
 *
 */
class TestDirsPathInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(ctx: ConfigurableApplicationContext) {
        val repositoryDirProp = if ("dir" in ctx.environment.activeProfiles) {
            "app.repository.dir.path"
        } else {
            "LOCAL_GIT_DIR"
        }
        val allDirProps = listOf(repositoryDirProp)
            .plus("SQLITE_DB_DIR")
            .plus("RECORD_ANALYZER_STORAGE_DIR")
            .plus("AUTOPILOT_STORAGE_DIR")
            .plus(ctx.environment.getProperty("EXTRA_TEST_DIR_PROPERTIES")?.split(",").orEmpty())
            .associateWith { newTestFolder(it) }
        ctx.environment.propertySources.addFirst(MapPropertySource("test-dirs", allDirProps))
    }
}
