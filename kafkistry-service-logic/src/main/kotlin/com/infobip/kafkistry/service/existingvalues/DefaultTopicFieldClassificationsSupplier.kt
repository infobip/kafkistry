package com.infobip.kafkistry.service.existingvalues

import com.infobip.kafkistry.model.FieldClassification
import org.springframework.stereotype.Component

const val CLASSIFICATION_PII = "PII"
const val CLASSIFICATION_SECRET = "SECRET"
const val CLASSIFICATION_IDENTIFIER = "IDENTIFIER"
const val CLASSIFICATION_TIME = "TIME"
const val CLASSIFICATION_QUANTITY = "QUANTITY"
const val CLASSIFICATION_TYPE = "TYPE"
const val CLASSIFICATION_TRACE = "TRACE"
const val CLASSIFICATION_FINANCIAL = "FINANCIAL"
const val CLASSIFICATION_INFRASTRUCTURAL = "INFRASTRUCTURAL"
const val CLASSIFICATION_STATUS = "STATUS"
const val CLASSIFICATION_NAME = "NAME"

@Component
class DefaultTopicFieldClassificationsSupplier : ExistingValuesSupplier {

    private val defaultClassifications = listOf(
        CLASSIFICATION_PII,
        CLASSIFICATION_SECRET,
        CLASSIFICATION_IDENTIFIER,
        CLASSIFICATION_TIME,
        CLASSIFICATION_QUANTITY,
        CLASSIFICATION_TYPE,
        CLASSIFICATION_TRACE,
        CLASSIFICATION_FINANCIAL,
        CLASSIFICATION_INFRASTRUCTURAL,
        CLASSIFICATION_STATUS,
        CLASSIFICATION_NAME,
    )

    override fun fieldClassifications(): List<FieldClassification> = defaultClassifications
}