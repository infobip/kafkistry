package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.NamedTypeCauseDescription

/**
 * Allows for custom topic inspection to be performed.
 * Output of inspection is done via [TopicExternalInspectCallback].
 * Multiple (or none) [TopicInspectionResultType] can be added as result of single external inspector invocation.
 * Extra information can (optional) be added with [#setExternalInfo] and result will be kept in
 * [TopicClusterStatus.externInspectInfo] under the key of [TopicExternalInspector.name].
 * Such exported extra info can then be used in custom UI template
 * (see: [com.infobip.kafkistry.webapp.TopicInspectExtensionProperties])
 */
interface TopicExternalInspector {

    val name: String get() = javaClass.name

    fun inspectTopic(ctx: TopicInspectCtx, outputCallback: TopicExternalInspectCallback)
}

interface TopicExternalInspectCallback {

    fun addStatusType(statusType: TopicInspectionResultType)

    fun addDescribedStatusType(statusTypeDescripton: NamedTypeCauseDescription<TopicInspectionResultType>)

    fun setExternalInfo(info: Any)
}
