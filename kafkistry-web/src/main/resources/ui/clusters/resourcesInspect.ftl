<#-- @ftlvariable name="clusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->
<#-- @ftlvariable name="diffModeEnabled"  type="java.lang.Boolean" -->

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<#assign hasOrphaned = clusterResources.combined.usage.orphanedReplicasCount gt 0>
<#assign showReplicas = !hasOrphaned>

<#function signClass number>
<#-- @ftlvariable name="number"  type="java.lang.Number" -->
    <#if !(diffModeEnabled??) || !diffModeEnabled>
        <#return "">
    </#if>
    <#if number gt 0>
        <#return "text-danger font-weight-bold">
    <#elseif number lt 0>
        <#return "text-success font-weight-bold">
    <#else>
        <#return "">
    </#if>
</#function>

<table class="table table-bordered m-0">
    <thead class="thead-dark">
    <tr>
        <th rowspan="2">Broker</th>
        <th colspan="2" class="text-center">Disk capacity</th>
        <th colspan="${showReplicas?then(3, 2)}" class="text-center">
            Total used
            <#assign totalDoc>
                Actual disk total usage of topic's replica log dirs.
            </#assign>
            <@info.icon tooltip = totalDoc/>
        </th>
        <th colspan="${showReplicas?then(3, 2)}" class="text-center">
            Possible usage
            <#assign boundedDoc>
                Possible usage of disk if all topics would reach their
                configured <code>retention.bytes</code> limit
            </#assign>
            <@info.icon tooltip = boundedDoc/>
        </th>
        <th colspan="${showReplicas?then(3, 2)}" class="text-center">
            Unbounded used
            <#assign unboundedDoc>
                Current usage of disk for all topics that have unbounded size retention
                <code>retention.bytes</code> = <code>-1</code> limit
            </#assign>
            <@info.icon tooltip = unboundedDoc/>
        </th>
        <#if hasOrphaned>
            <th colspan="2" class="text-center">
                Orphaned used
                <#assign orphanedDoc>
                    Replica dirs that exist on broker but not currently being part of topic's assignment.
                </#assign>
                <@info.icon tooltip = orphanedDoc/>
            </th>
        </#if>
    </tr>
    <tr>
        <th>Total</th>
        <th>Free</th>

        <th>Bytes</th>
        <th>%&nbsp;capacity</th>
        <#if showReplicas>
            <th>Replicas</th>
        </#if>

        <th>Bytes</th>
        <th>%&nbsp;capacity</th>
        <#if showReplicas>
            <th>Replicas</th>
        </#if>

        <th>Bytes</th>
        <th>%&nbsp;used</th>
        <#if showReplicas>
            <th>Replicas</th>
        </#if>

        <#if hasOrphaned>
            <th>Bytes</th>
            <th>Replicas</th>
        </#if>
    </tr>
    </thead>
    <#macro diskUsages broker, disk>
    <#-- @ftlvariable name="disk" type="com.infobip.kafkistry.service.resources.BrokerDisk" -->
        <#assign usages = disk.usage>
        <th>${broker}</th>
        <td>
            <#if usages.totalCapacityBytes??>
                <span class="${signClass(usages.totalCapacityBytes)}">
                    ${util.prettyDataSize(usages.totalCapacityBytes, diffModeEnabled!false)}
                </span>
            <#else>
                ---
            </#if>
        </td>
        <td>
            <#if usages.freeCapacityBytes??>
                <span class="${signClass(usages.freeCapacityBytes)}">
                    ${util.prettyDataSize(usages.freeCapacityBytes, diffModeEnabled!false)}
                </span>
            <#else>
                ---
            </#if>
        </td>

        <#assign usageLevelClass = util.usageLevelToHtmlClass(disk.portions.usageLevel)>
        <td class="${usageLevelClass}">
            <#if usages.totalUsedBytes??>
                <span class="${signClass(usages.totalUsedBytes)}">
                    ${util.prettyDataSize(usages.totalUsedBytes, diffModeEnabled!false)}
                </span>
            <#else>
                ---
            </#if>
        </td>
        <td class="${usageLevelClass}">
            <#if disk.portions.usedPercentOfCapacity??>
                <span class="${signClass(disk.portions.usedPercentOfCapacity)}">
                    ${util.prettyNumber(disk.portions.usedPercentOfCapacity, diffModeEnabled!false)}%
                </span>
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
            <td>
                <#if usages.replicasCount??>
                    <span class="${signClass(usages.replicasCount)}">
                        ${util.numberToString(usages.replicasCount, diffModeEnabled!false)}
                    </span>
                <#else>
                    ---
                </#if>
            </td>
        </#if>

        <#assign possibleUsageLevelClass = util.usageLevelToHtmlClass(disk.portions.possibleUsageLevel)>
        <td class="${possibleUsageLevelClass}">
            <span class="${signClass(usages.boundedSizePossibleUsedBytes)}">
                ${util.prettyDataSize(usages.boundedSizePossibleUsedBytes, diffModeEnabled!false)}
            </span>
        </td>
        <td class="${possibleUsageLevelClass}">
            <#if disk.portions.possibleUsedPercentOfCapacity??>
                <span class="${signClass(disk.portions.possibleUsedPercentOfCapacity)}">
                    ${util.prettyNumber(disk.portions.possibleUsedPercentOfCapacity, diffModeEnabled!false)}%
                </span>
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
            <td>
                <span class="${signClass(usages.boundedReplicasCount)}">
                    ${util.numberToString(usages.boundedReplicasCount, diffModeEnabled!false)}
                </span>
            </td>
        </#if>

        <td>
            ${util.prettyDataSize(usages.unboundedSizeUsedBytes)}
        </td>
        <td>
            <#if disk.portions.unboundedUsedPercentOfTotalUsed??>
                <span class="${signClass(disk.portions.unboundedUsedPercentOfTotalUsed)}">
                    ${util.prettyNumber(disk.portions.unboundedUsedPercentOfTotalUsed, diffModeEnabled!false)}%
                </span>
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
            <td>
                <span class="${signClass(usages.unboundedReplicasCount)}">
                    ${util.numberToString(usages.unboundedReplicasCount, diffModeEnabled!false)}
                </span>
            </td>
        </#if>

        <#if hasOrphaned>
            <td>
                <span class="${signClass(usages.orphanedReplicasSizeUsedBytes)}">
                    ${util.prettyDataSize(usages.orphanedReplicasSizeUsedBytes, diffModeEnabled!false)}
                </span>
            </td>
            <td>
                <span class="${signClass(usages.orphanedReplicasCount)}">
                    ${util.numberToString(usages.orphanedReplicasCount, diffModeEnabled!false)}
                </span>
            </td>
        </#if>
    </#macro>

    <tr class="">
        <@diskUsages broker="ALL" disk = clusterResources.combined/>
    </tr>
    <tr>
        <td colspan="100" class="bg-light p-1"></td>
    </tr>
    <#list clusterResources.brokerUsages as brokerId, brokerUsages>
        <tr class="table-sm">
            <@diskUsages broker="${brokerId}" disk = brokerUsages/>
        </tr>
    </#list>
</table>
