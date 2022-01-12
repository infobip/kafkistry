package com.infobip.kafkistry.service

import org.springframework.http.HttpStatus
import java.lang.RuntimeException

abstract class KafkistryException : RuntimeException {
    constructor(msg: String) : super(msg)
    constructor(cause: Throwable) : super(cause)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    open val httpStatus: Int = HttpStatus.INTERNAL_SERVER_ERROR.value()
}

class KafkaClusterManagementException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(cause: Throwable) : super(cause)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

class KafkistryIntegrityException(msg: String) : KafkistryException(msg)

class KafkistryIllegalStateException(msg: String) : KafkistryException(msg)

class KafkistryUnsupportedOperationException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.METHOD_NOT_ALLOWED.value()
}

class KafkistryValidationException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.BAD_REQUEST.value()
}

class KafkistryGitException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

class KafkistryStorageException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

class TopicWizardException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    override val httpStatus: Int = HttpStatus.BAD_REQUEST.value()
}

class KafkistryConsumeException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

class KafkistryClusterReadException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

class KafkistrySQLException : KafkistryException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable) : super(msg, cause)
}

open class KafkistryPermissionException(msg: String) : KafkistryException(msg) {
    override val httpStatus: Int = HttpStatus.FORBIDDEN.value()
}