package com.infobip.kafkistry.service

import org.springframework.http.HttpStatus
import java.lang.RuntimeException

abstract class KafkistryException : RuntimeException {
    constructor(msg: String) : super(msg)
    constructor(cause: Throwable) : super(cause)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    open val httpStatus: Int = HttpStatus.INTERNAL_SERVER_ERROR.value()
}

open class KafkaClusterManagementException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(cause: Throwable) : super(cause)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistryIntegrityException(msg: String) : KafkistryException(msg)

open class KafkistryIllegalStateException(msg: String) : KafkistryException(msg)

open class KafkistryUnsupportedOperationException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.METHOD_NOT_ALLOWED.value()
}

open class KafkistryValidationException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.BAD_REQUEST.value()
}

open class KafkistryGitException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistryStorageException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class TopicWizardException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    override val httpStatus: Int = HttpStatus.BAD_REQUEST.value()
}

open class KafkistryConsumeException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistryClusterReadException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistrySQLException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistryPermissionException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.FORBIDDEN.value()
}