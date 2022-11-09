package com.infobip.kafkistry.utils.test

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.nio.file.Files

private val log = LoggerFactory.getLogger("testFolderFactory")

fun newTestFolder(forWhat: String): String {
    return Files.createTempDirectory("test-tmp-dir-$forWhat-").toFile().also {
        log.info("Created test dir: '{}'", it.absolutePath)
        Runtime.getRuntime().addShutdownHook(Thread ({
            log.info("Cleaning test dir: '{}'", it.absolutePath)
            FileUtils.deleteDirectory(it)
        }, "ShutdownCleanTestDirHook"))
    }.absolutePath
}
