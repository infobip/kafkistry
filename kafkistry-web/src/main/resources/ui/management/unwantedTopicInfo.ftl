<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="topicConsumerGroups" type="java.util.List<com.infobip.kafkistry.service.consumers.KafkaConsumerGroup>" -->
<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->

<#-- @ftlvariable name="bulkIterateBy" type="java.lang.String" -->

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumerUtil>

<tr class="thead-dark">
    <th colspan="100" class="text-center_">
        <#switch bulkIterateBy!''>
            <#case "TOPIC">
                Topic: ${topicName}
                <#break>
            <#case "CLUSTER">
                Cluster: ${clusterIdentifier}
                <#break>
            <#default>
                ${topicName} @ ${clusterIdentifier}
        </#switch>
    </th>
</tr>
<tr>
    <th>Producing</th>
    <td>
        <#if topicOffsets?? && topicOffsets != "">
            <#include "../topics/topicOffsetsStatus.ftl">
        <#else>
            <i>N/A</i>
        </#if>
    </td>
</tr>
<tr>
    <th>Consumers</th>
    <td>
        <#if topicConsumerGroups?size == 0>
            <i>(no consumer groups reading from this topic)</i>
        <#else>
            <table class="table table-sm mb-0">
                <thead class="thead-dark">
                <tr>
                    <th style="width: 60%">Group</th>
                    <th>Status</th>
                    <th>Lag</th>
                </tr>
                </thead>
                <#list topicConsumerGroups as consumerGroup>
                    <tr>
                        <td>
                            <a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroup.groupId, topicName)}">
                                ${consumerGroup.groupId}
                            </a>
                        </td>
                        <td>
                            <@util.namedTypeStatusAlert type=consumerGroup.status/>
                        </td>
                        <#list consumerGroup.topicMembers as topicMember>
                            <#if topicMember.topicName == topicName>
                                <td>
                                    <@util.namedTypeStatusAlert type=topicMember.lag.status/>
                                </td>
                            </#if>
                        </#list>
                    </tr>
                </#list>
            </table>
        </#if>

    </td>
</tr>
<#if bulkIterateBy??>
    <tr class="unwanted-topic" data-cluster-identifier="${clusterIdentifier}" data-topic-name="${topicName}">
        <td colspan="100">
            <#switch bulkIterateBy>
                <#case "TOPIC">
                    <#assign statusId = "op-status-"+topicName>
                    <#break>
                <#case "CLUSTER">
                    <#assign statusId = "op-status-"+clusterIdentifier>
                    <#break>
            </#switch>
            <#include "../common/serverOpStatus.ftl">
        </td>
    </tr>
</#if>
