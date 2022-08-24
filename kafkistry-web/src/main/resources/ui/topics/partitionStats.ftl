<#-- @ftlvariable name="topicName"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="topicStatus"  type="com.infobip.kafkistry.service.topic.TopicClusterStatus" -->
<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos" -->
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
            <#assign lowPartitions = topicStatus.externInspectInfo["com.infobip.kafkistry.service.topic.inspectors.TopicUnderprovisionedRetentionInspector"]>
            <#-- @ftlvariable name="lowPartitions" type="java.util.Map<java.lang.Integer, java.lang.Integer>" -->
            <#list partitions?sort as partition>
                <tr>
                    <th>${partition?c}</th>
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
                    <td>
                        <#if (topicOffsets.partitionMessageRate?api.get(partition).upTo15MinRate)??>
                            <#assign rateMsgPerSec = topicOffsets.partitionMessageRate?api.get(partition).upTo15MinRate>
                            ${_util.prettyNumber(rateMsgPerSec)} msg/sec
                        <#else>
                            ---
                        </#if>
                    </td>
                    <#if (topicReplicas.partitionBrokerReplicas?api.get(partition))??>
                        <#assign partitionBrokerReplicas = topicReplicas.partitionBrokerReplicas?api.get(partition)>
                        <#assign sizeBytes = 0>
                        <#list partitionBrokerReplicas as broker, replica>
                            <#assign sizeBytes = (replica.sizeBytes gt sizeBytes)?then(replica.sizeBytes, sizeBytes)>
                        </#list>
                        <td>
                            ${_util.prettyDataSize(sizeBytes)}
                        </td>
                        <#if retentionBytes gt 0>
                            <td>
                                ${_util.prettyNumber(100*sizeBytes/retentionBytes)} %
                            </td>
                        <#else>
                            <td>---</td>
                        </#if>
                    <#else>
                        <td>---</td>
                        <td>---</td>
                    </#if>
                    <#if (oldestRecordAges?api.get(partition))??>
                        <#assign ageMs = oldestRecordAges?api.get(partition)>
                        <#assign underprovisioned = (lowPartitions?api.get(partition))??>
                        <td class="<#if underprovisioned>value-mismatch</#if>">
                            <span>${_util.prettyDuration(ageMs / 1000)}</span>
                        </td>
                        <#if retentionMs gt 0>
                            <td class="<#if underprovisioned>value-mismatch</#if>">
                                ${_util.prettyNumber(100*ageMs/retentionMs)} %
                            </td>
                        <#else>
                            <td>---</td>
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
                            ${oldestDisabledCell}
                            ${oldestDisabledCell}
                        <#else>
                            <td>---</td>
                            <td>---</td>
                        </#if>
                    </#if>
                </tr>
            </#list>
        </table>
    </div>
</div>