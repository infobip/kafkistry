package com.infobip.kafkistry.utils.test

import org.apache.commons.io.FileUtils
import java.nio.file.Files

fun newTestFolder(forWhat: String): String {
    return Files.createTempDirectory("test-tmp-dir-$forWhat-").toFile().also {
        Runtime.getRuntime().addShutdownHook(Thread {
            FileUtils.deleteDirectory(it)
        })
    }.absolutePath
}
