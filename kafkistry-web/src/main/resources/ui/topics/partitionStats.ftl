<#-- @ftlvariable name="topicName"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="topicStatus"  type="com.infobip.kafkistry.service.topic.TopicClusterStatus" -->
<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->
<!-- @ftlvariable name="oldestRecordAgesDisabled" type="java.lang.Boolean" -->
<!-- @ftlvariable name="oldestRecordAges" type="java.util.Map<java.lang.Integer, java.lang.Long>" -->
<#-- @ftlvariable name="topicStatus"  type="com.infobip.kafkistry.service.topic.TopicClusterStatus" -->

<#import "../common/util.ftl" as _util>
<#import "../common/infoIcon.ftl" as _info_>
<#import "../sql/sqlQueries.ftl" as sql>

<#assign retentionMs = (topicStatus.existingTopicInfo.config["retention.ms"].value?number)!-1>
<#assign retentionBytes = (topicStatus.existingTopicInfo.config["retention.bytes"].value?number)!-1>

<div class="card">
    <div class="card-header">
        <span class="h4">Partition stats</span>
        <span class="ml-3">
            <@sql.topicProduceRates cluster=clusterIdentifier topic=topicName/>
            <@sql.topicSizeVsTimeRetention cluster=clusterIdentifier topic=topicName/>
        </span>
    </div>
    <div class="card-body p-0">
        <table class="table table-sm m-0">
            <thead class="thead-dark">
            <tr>
                <th>Partition</th>
                <th>Brokers</th>
                <th>Messages <#include "numMessagesEstimateDoc.ftl"></th>
                <th>Produce rate</th>
                <th>Replica size</th>
                <th>% of retention.bytes</th>
                <th>Oldest record age</th>
                <th>% of retention.ms</th>
            </tr>
            </thead>
            <#assign partitions = (topicOffsets.partitionsOffsets?keys)!(topicReplicas.partitionBrokerReplicas?keys)!(oldestRecordAges?keys)![]>
            <#assign lowProvisionPartitions = (topicStatus.externInspectInfo["com.infobip.kafkistry.service.topic.inspectors.TopicUnderprovisionedRetentionInspector"])!{}>
            <#-- @ftlvariable name="lowProvisionPartitions" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.topic.inspectors.UnderprovisionedPartitionStats>" -->
            <#assign unevenRatePartitions = (topicStatus.externInspectInfo["com.infobip.kafkistry.service.topic.inspectors.TopicUnevenPartitionProducingInspector"])!{}>
            <#-- @ftlvariable name="unevenRatePartitions" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.topic.inspectors.UnevenPartitionRates>" -->
            <#list partitions?sort as partition>
                <tr>
                    <#-- Partition -->
                    <th>${partition?c}</th>
                    <#-- Brokers -->
                    <td>
                        <#if (topicStatus.existingTopicInfo.partitionsAssignments)??>
                            <#list topicStatus.existingTopicInfo.partitionsAssignments as assignment>
                                <#if assignment.partition == partition>
                                    <#list assignment.replicasAssignments as replica>
                                        ${replica.brokerId?c}<#if !replica?is_last>,</#if>
                                    </#list>
                                </#if>
                            </#list>
                        <#else>
                            ---
                        </#if>
                    </td>
                    <#-- Messages -->
                    <td>
                        <#if (topicOffsets.partitionsOffsets?api.get(partition))??>
                            <#assign offsets = topicOffsets.partitionsOffsets?api.get(partition)>
                            ${_util.prettyNumber(offsets.end - offsets.begin)}
                            <#assign offsetTooltip>
                                <strong>Begin</strong>: ${offsets.begin?c}<br/>
                                <strong>End</strong>: ${offsets.end?c}<br/>
                            </#assign>
                            <@_info_.icon tooltip=offsetTooltip/>
                        <#else>
                            ---
                        </#if>
                    </td>
                    <#if (topicOffsets.partitionMessageRate?api.get(partition).upTo15MinRate)??>
                        <#assign rateMsgPerSec = topicOffsets.partitionMessageRate?api.get(partition).upTo15MinRate>
                        <#assign lowRate = false>
                        <#assign highRate = false>
                        <#if unevenRatePartitions?size gt 0>
                            <#assign lowRate = (unevenRatePartitions.lowRate?api.get(partition))??>
                            <#assign highRate = (unevenRatePartitions.highRate?api.get(partition))??>
                        </#if>
                        <#-- Produce rate -->
                        <td class="<#if highRate>value-mismatch</#if><#if lowRate>alert-warning</#if>">
                            ${_util.prettyNumber(rateMsgPerSec)} msg/sec
                            <#if highRate>
                                <@_info_.icon tooltip="This is a <strong>high</strong> produce rate partition"/>
                            </#if>
                            <#if lowRate>
                                <@_info_.icon tooltip="This is a <strong>low</strong> produce rate partition"/>
                            </#if>
                        </td>
                    <#else>
                        <td>---</td>    <#-- Produce rate -->
                    </#if>

                    <#if (topicReplicas.partitionBrokerReplicas?api.get(partition))??>
                        <#assign partitionBrokerReplicas = topicReplicas.partitionBrokerReplicas?api.get(partition)>
                        <#assign sizeBytes = 0>
                        <#list partitionBrokerReplicas as broker, replica>
                            <#assign sizeBytes = (replica.sizeBytes gt sizeBytes)?then(replica.sizeBytes, sizeBytes)>
                        </#list>
                        <#-- Replica size -->
                        <td>
                            ${_util.prettyDataSize(sizeBytes)}
                        </td>
                        <#if retentionBytes gt 0>
                            <#-- % of retention.bytes -->
                            <td>
                                ${_util.prettyNumber(100*sizeBytes/retentionBytes)} %
                            </td>
                        <#else>
                            <#-- % of retention.bytes -->
                            <td>---</td>
                        </#if>
                    <#else>
                        <td>---</td>    <#-- Replica size -->
                        <td>---</td>    <#-- % of retention.bytes -->
                    </#if>
                    <#if (oldestRecordAges?api.get(partition))??>
                        <#assign ageMs = oldestRecordAges?api.get(partition)>
                        <#assign underprovisioned = false>
                        <#if lowProvisionPartitions?size gt 0>
                            <#assign underprovisioned = (lowProvisionPartitions?api.get(partition))??>
                        </#if>
                        <#-- Oldest record age -->
                        <td class="<#if underprovisioned>value-mismatch</#if>">
                            <span>${_util.prettyDuration(ageMs / 1000)}</span>
                            <#if underprovisioned>
                                <#assign underprovisionTooltip>
                                    This partition has lower <i>effective retention</i> than wanted
                                    <code>retention.ms</code> due to low <code>retention.bytes</code>
                                </#assign>
                                <@_info_.icon tooltip=underprovisionTooltip/>
                            </#if>
                        </td>
                        <#if retentionMs gt 0>
                            <#-- % of retention.ms -->
                            <td class="<#if underprovisioned>value-mismatch</#if>">
                                ${_util.prettyNumber(100*ageMs/retentionMs)} %
                            </td>
                        <#else>
                            <td>---</td>    <#-- % of retention.ms -->
                        </#if>
                    <#else>
                        <#assign oldestDisabledDoc>
                            Sampling of oldest records timestamps is <strong>disabled</strong><br/>
                            Enable it by setting one of following application properties:
                            <ul>
                                <li><code>OLDEST_RECORD_AGE_ENABLED</code> = <code>true</code></li>
                                <li><code>app.oldest-record-age.enabled</code> = <code>true</code></li>
                            </ul>
                        </#assign>
                        <#assign oldestDisabledCell>
                            <td class="small">
                                <span class="badge badge-warning">DISABLED</span>
                                <@_info_.icon tooltip=oldestDisabledDoc/>
                            </td>
                        </#assign>
                        <#if oldestRecordAgesDisabled>
                            ${oldestDisabledCell}    <#-- Oldest record age -->
                            ${oldestDisabledCell}    <#-- % of retention.ms -->
                        <#else>
                            <td>---</td>    <#-- Oldest record age -->
                            <td>---</td>    <#-- % of retention.ms -->
                        </#if>
                    </#if>
                </tr>
            </#list>
        </table>
    </div>
</div>