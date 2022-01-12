package com.infobip.kafkistry.repository

import com.infobip.kafkistry.model.QuotaDescription
import com.infobip.kafkistry.model.QuotaEntityID

interface KafkaQuotasRepository : RequestingKeyValueRepository<QuotaEntityID, QuotaDescription>

class StorageKafkaQuotasRepository(
    delegate: RequestingKeyValueRepository<QuotaEntityID, QuotaDescription>
) : DelegatingRequestingKeyValueRepository<QuotaEntityID, QuotaDescription>(delegate), KafkaQuotasRepository