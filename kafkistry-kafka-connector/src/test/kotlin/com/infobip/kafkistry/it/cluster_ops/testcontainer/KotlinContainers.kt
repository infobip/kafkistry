package com.infobip.kafkistry.it.cluster_ops.testcontainer

import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.GenericContainer
import java.io.File

open class KGenericContainer(dockerImageName: String): GenericContainer<KGenericContainer>(dockerImageName)

open class KDockerComposeContainer(identifier: String, file: File)
    : DockerComposeContainer<KDockerComposeContainer>(identifier, file)