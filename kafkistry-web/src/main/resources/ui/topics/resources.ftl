<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicResources" type="com.infobip.kafkistry.service.resources.TopicClusterDiskUsageExt" -->

<#import "../common/util.ftl" as rUtil>
<#import "../common/infoIcon.ftl" as rInfo>

<div class="card">
    <div class="card-header">
        <h4>
            Disk resource usage of topic on ${clusterIdentifier}
            <a href="${appUrl.clusters().showClusterResources(clusterIdentifier)}"
                class="btn btn-sm btn-outline-dark" target="_blank">🔍</a>
        </h4>
    </div>
    <div class="card-body p-0">
        <#assign hasActual = topicResources.combined.usage.actualUsedBytes??>
        <#assign hasPossible = topicResources.combined.usage.retentionBoundedBytes??>
        <#assign hasOrphaned = topicResources.combined.usage.orphanedReplicasCount gt 0>
        <table class="table table-bordered m-0">
            <thead class="thead-dark table-sm text-center">
            <tr>
                <th rowspan="2">Broker</th>
                <th rowspan="2">Replicas</th>
                <#if hasActual>
                    <th colspan="3" class="text-center">Current disk usage</th>
                </#if>
                <#if hasPossible>
                    <th colspan="3" class="text-center">
                        Possible disk usage
                        <#assign possibleUsageDoc>
                            Possible usage of disk if this topic would reach it's <code>retention.bytes</code> limit
                        </#assign>
                        <@rInfo.icon tooltip = possibleUsageDoc/>
                    </th>
                </#if>
                <th rowspan="2">
                    Required usage
                    <#assign possibleUsageDoc>
                        How much of usage is required/expected/predicted by resource requirement expectations
                        for this topic on this cluster
                    </#assign>
                    <@rInfo.icon tooltip = possibleUsageDoc/>
                </th>
                <#if hasOrphaned>
                    <th rowspan="2"># orphan replicas</th>
                </#if>
                <th colspan="${hasPossible?then(4,2)}" class="text-center">Cluster broker(s)</th>
            </tr>
            <tr>
                <#if hasActual>
                    <th>Total</th>
                    <th>% of broker used</th>
                    <th>% of required</th>
                </#if>
                <#if hasPossible>
                    <th>Total</th>
                    <th>% of broker used</th>
                    <th>% of required</th>
                </#if>
                <th>
                    Current used
                    <#assign clusterUsedDoc>
                        Current disk usage of all replicas on particular broker
                    </#assign>
                    <@rInfo.icon tooltip = clusterUsedDoc/>
                </th>
                <#if hasPossible>
                    <th>
                        Possible usage
                        <#assign clusterPossibleDoc>
                            Possible usage of disk if this topic would reach it's <code>retention.bytes</code> limit,
                            and all other topics would remain at current usage
                        </#assign>
                        <@rInfo.icon tooltip = clusterPossibleDoc/>
                    </th>
                    <th>
                        Max possible usage
                        <#assign clusterTotalPossibleDoc>
                            Possible usage of disk if all topics, including this topic, would reach it's <code>retention.bytes</code> limit
                        </#assign>
                        <@rInfo.icon tooltip = clusterTotalPossibleDoc/>
                    </th>
                </#if>
                <th>Capacity</th>
            </tr>
            </thead>
            <#macro diskUsage broker, usage, portions, brokerUsage, brokerPortions worstPossibleUsageLevel="NONE" worstTotalPossibleUsageLevel="NONE">
            <#-- @ftlvariable name="usage" type="com.infobip.kafkistry.service.resources.TopicDiskUsageExt" -->
            <#-- @ftlvariable name="portions" type="com.infobip.kafkistry.service.resources.UsagePortions" -->
            <#-- @ftlvariable name="brokerUsage" type="type="com.infobip.kafkistry.service.resources.BrokerDiskUsage" -->
            <#-- @ftlvariable name="brokerPortions" type="com.infobip.kafkistry.service.resources.BrokerDiskPortions" -->
            <#-- @ftlvariable name="worstPossibleUsageLevel" type="com.infobip.kafkistry.service.resources.UsageLevel" -->
            <#-- @ftlvariable name="worstTotalPossibleUsageLevel" type="com.infobip.kafkistry.service.resources.UsageLevel" -->
                <th>
                    ${broker}
                    <#if broker == "ALL">
                        <span class="when-collapsed" title="expand...">▼</span>
                        <span class="when-not-collapsed" title="collapse...">△</span>
                    </#if>
                </th>
                <td>
                    ${usage.usage.replicasCount}
                </td>
                <#if hasActual>
                    <td>
                        <#if usage.usage.actualUsedBytes??>
                            ${rUtil.prettyDataSize(usage.usage.actualUsedBytes)}
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if portions.actualUsedBytesPercentOfBrokerTotal??>
                            ${rUtil.prettyNumber(portions.actualUsedBytesPercentOfBrokerTotal)}%
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if portions.actualUsedBytesPercentOfExpected??>
                            ${rUtil.prettyNumber(portions.actualUsedBytesPercentOfExpected)}%
                        <#else>
                            ---
                        </#if>
                    </td>
                </#if>
                <#if hasPossible>
                    <td>
                        <#if usage.usage.retentionBoundedBytes??>
                            ${rUtil.prettyDataSize(usage.usage.retentionBoundedBytes)}
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if portions.retentionBoundedBytesPercentOfBrokerTotal??>
                            ${rUtil.prettyNumber(portions.retentionBoundedBytesPercentOfBrokerTotal)}%
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if portions.retentionBoundedBytesPercentOfExpected??>
                            ${rUtil.prettyNumber(portions.retentionBoundedBytesPercentOfExpected)}%
                        <#else>
                            ---
                        </#if>
                    </td>
                </#if>
                <td>
                    <#if usage.usage.expectedUsageBytes??>
                        ${rUtil.prettyDataSize(usage.usage.expectedUsageBytes)}
                    <#else>
                        ---
                    </#if>
                </td>

                <#if hasOrphaned>
                    <td>${usage.usage.orphanedReplicasCount}</td>
                </#if>

                <#assign brokerUsageLevelClass = rUtil.usageLevelToHtmlClass(brokerPortions.usageLevel)>
                <td class="${brokerUsageLevelClass}"
                    <#if brokerPortions.usedPercentOfCapacity??>
                        title="${rUtil.prettyNumber(brokerPortions.usedPercentOfCapacity)}%"
                    </#if>
                >
                    <#if brokerUsage.totalUsedBytes??>
                        ${rUtil.prettyDataSize(brokerUsage.totalUsedBytes)}
                    <#else>
                        ---
                    </#if>
                </td>
                <#if hasPossible>
                    <#assign possibleUsageLevel = (worstPossibleUsageLevel == "NONE")?then(portions.possibleClusterUsageLevel, worstPossibleUsageLevel)>
                    <#assign possibleUsageLevelClass = rUtil.usageLevelToHtmlClass(possibleUsageLevel)>
                    <td class="${possibleUsageLevelClass}"
                        <#if portions.retentionBoundedBrokerTotalBytesPercentOfCapacity??>
                            title="${rUtil.prettyNumber(portions.retentionBoundedBrokerTotalBytesPercentOfCapacity)}%"
                        </#if>
                    >
                        <#if usage.retentionBoundedBrokerTotalBytes??>
                            ${rUtil.prettyDataSize(usage.retentionBoundedBrokerTotalBytes)}
                        <#else>
                            ---
                        </#if>
                    </td>
                    <#assign totalPossibleUsageLevel = (worstTotalPossibleUsageLevel == "NONE")?then(portions.totalPossibleClusterUsageLevel, worstTotalPossibleUsageLevel)>
                    <#assign totalPossibleUsageLevelClass = rUtil.usageLevelToHtmlClass(totalPossibleUsageLevel)>
                    <td class="${totalPossibleUsageLevelClass}"
                        <#if portions.retentionBoundedBrokerPossibleBytesPercentOfCapacity??>
                            title="${rUtil.prettyNumber(portions.retentionBoundedBrokerPossibleBytesPercentOfCapacity)}%"
                        </#if>
                    >
                        <#if usage.retentionBoundedBrokerPossibleBytes??>
                            ${rUtil.prettyDataSize(usage.retentionBoundedBrokerPossibleBytes)}
                        <#else>
                            ---
                        </#if>
                    </td>
                </#if>
                <td>
                    <#if brokerUsage.totalCapacityBytes??>
                        ${rUtil.prettyDataSize(brokerUsage.totalCapacityBytes)}
                    <#else>
                        ---
                    </#if>
                </td>
            </#macro>
            <tr class="collapsed" data-target=".broker-resources-row.collapse-${clusterIdentifier?url}" data-toggle="collapsing">
                <@diskUsage broker="ALL"
                    usage=topicResources.combined
                    portions=topicResources.combinedPortions
                    brokerUsage=topicResources.clusterDiskUsage.combined.usage
                    brokerPortions=topicResources.clusterDiskUsage.combined.portions
                    worstPossibleUsageLevel=topicResources.worstPossibleClusterUsageLevel
                    worstTotalPossibleUsageLevel=topicResources.worstTotalPossibleClusterUsageLevel
                />
            </tr>
            <tr>
                <td colspan="100" class="bg-light p-1"></td>
            </tr>
            <#list topicResources.brokerUsages as brokerId, brokerUsage>
                <tr class="small table-sm broker-resources-row collapse-${clusterIdentifier?url} collapseable">
                    <@diskUsage broker="${brokerId}"
                        usage=brokerUsage
                        portions=topicResources.brokerPortions?api.get(brokerId)
                        brokerUsage=topicResources.clusterDiskUsage.brokerUsages?api.get(brokerId).usage
                        brokerPortions=topicResources.clusterDiskUsage.brokerUsages?api.get(brokerId).portions
                    />
                </tr>
            </#list>
        </table>
    </div>
</div>
