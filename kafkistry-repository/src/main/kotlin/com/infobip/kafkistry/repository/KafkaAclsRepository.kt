package com.infobip.kafkistry.repository

import com.infobip.kafkistry.model.PrincipalAclRules
import com.infobip.kafkistry.model.PrincipalId

interface KafkaAclsRepository : RequestingKeyValueRepository<PrincipalId, PrincipalAclRules>
interface KafkaAclsRefreshableRepository : KafkaAclsRepository, RefreshableRepository

class StorageKafkaAclsRepository(
    delegate: RequestingKeyValueRepository<PrincipalId, PrincipalAclRules>
) : DelegatingRequestingKeyValueRepository<PrincipalId, PrincipalAclRules>(delegate), KafkaAclsRefreshableRepository
