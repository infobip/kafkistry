<#import "../common/util.ftl" as _util_>

<#macro usageHeaderSectionCells>
    <th rowspan="2">Expected avg <br/> msg rate</th>
    <th colspan="3" class="text-center">Required expected disk usage</th>
    <th colspan="4" class="text-center">Data IO</th>
</#macro>

<#macro usageHeaderCells>
    <th>Total</th>
    <th>Per broker</th>
    <th>Per partition replica</th>

    <th>Total from producer</th>
    <th>Per partition</th>
    <th>Per broker IN <br/>(produce + sync)</th>
    <th>Per broker OUT <br/>(sync only)</th>
</#macro>

<#macro usageValuesCells optionalResourceRequiredUsages>
<#-- @ftlvariable name="optionalUsages" type="com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.TopicResourceRequiredUsages>" -->
    <#if optionalResourceRequiredUsages.value??>
        <#assign usages = optionalResourceRequiredUsages.value>

        <td>
            <#if usages.messagesPerSec=0.0>
                0 msg/sec
            <#elseif usages.messagesPerSec gte 1>
                ${_util_.prettyNumber(usages.messagesPerSec)+" msg/sec"}
            <#else>
                <#assign msgFreq = 1 / usages.messagesPerSec>
                1 msg every ${_util_.prettyDuration(msgFreq)}
            </#if>
        </td>

        <td>${_util_.prettyDataSize(usages.totalDiskUsageBytes)}</td>
        <td><#if usages.diskUsagePerBroker??>${_util_.prettyDataSize(usages.diskUsagePerBroker)}<#else>N/A</#if></td>
        <td>${_util_.prettyDataSize(usages.diskUsagePerPartitionReplica)}</td>

        <td>${_util_.prettyDataSize(usages.bytesPerSec)+"/sec"}</td>
        <td>${_util_.prettyDataSize(usages.partitionInBytesPerSec)+"/sec"}</td>
        <td><#if usages.brokerInBytesPerSec??>${_util_.prettyDataSize(usages.brokerInBytesPerSec)+"/sec"}<#else>N/A</#if></td>
        <td><#if usages.brokerSyncBytesPerSec??>${_util_.prettyDataSize(usages.brokerSyncBytesPerSec)+"/sec"}<#else>N/A</#if></td>
    <#else>
        <td colspan="8">N/A: <code>${optionalResourceRequiredUsages.absentReason}</code></td>
    </#if>
</#macro>
