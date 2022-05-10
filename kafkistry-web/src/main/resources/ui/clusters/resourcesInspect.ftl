<#-- @ftlvariable name="clusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<#assign hasOrphaned = clusterResources.combined.usage.orphanedReplicasCount gt 0>
<#assign showReplicas = !hasOrphaned>

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
                ${util.prettyDataSize(usages.totalCapacityBytes)}
            <#else>
                ---
            </#if>
        </td>
        <td>
            <#if usages.freeCapacityBytes??>
                ${util.prettyDataSize(usages.freeCapacityBytes)}
            <#else>
                ---
            </#if>
        </td>

        <#assign usageLevelClass = util.usageLevelToHtmlClass(disk.portions.usageLevel)>
        <td class="${usageLevelClass}">
            <#if usages.totalUsedBytes??>
                ${util.prettyDataSize(usages.totalUsedBytes)}
            <#else>
                ---
            </#if>
        </td>
        <td class="${usageLevelClass}">
            <#if disk.portions.usedPercentOfCapacity??>
                ${util.prettyNumber(disk.portions.usedPercentOfCapacity)}%
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
            <td>${usages.replicasCount!'---'}</td>
        </#if>

        <#assign possibleUsageLevelClass = util.usageLevelToHtmlClass(disk.portions.possibleUsageLevel)>
        <td class="${possibleUsageLevelClass}">
            ${util.prettyDataSize(usages.boundedSizePossibleUsedBytes)}
        </td>
        <td class="${possibleUsageLevelClass}">
            <#if disk.portions.possibleUsedPercentOfCapacity??>
                ${util.prettyNumber(disk.portions.possibleUsedPercentOfCapacity)}%
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
            <td>${usages.boundedReplicasCount}</td>
        </#if>

        <td>
            ${util.prettyDataSize(usages.unboundedSizeUsedBytes)}
        </td>
        <td>
            <#if disk.portions.unboundedUsedPercentOfTotalUsed??>
                ${util.prettyNumber(disk.portions.unboundedUsedPercentOfTotalUsed)}%
            <#else>
                ---
            </#if>
        </td>
        <#if showReplicas>
        <td>${usages.unboundedReplicasCount}</td>
        </#if>

        <#if hasOrphaned>
            <td>
                ${util.prettyDataSize(usages.orphanedReplicasSizeUsedBytes)}
            </td>
            <td>
                ${usages.orphanedReplicasCount}
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
