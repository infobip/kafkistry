<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="expectedTopicInfo" type="com.infobip.kafkistry.service.ExpectedTopicInfo" -->
<#-- @ftlvariable name="bulkIterateBy" type="java.lang.String" -->

<#import "../common/util.ftl" as etUtil>

<tr class="thead-dark">
    <th colspan="100" class="text-center_">
        <#switch bulkIterateBy!''>
            <#case "TOPIC">
                Topic: ${expectedTopicInfo.name}
                <#break>
            <#case "CLUSTER">
                Cluster: ${clusterIdentifier}
                <#break>
            <#default>
                ${expectedTopicInfo.name} @ ${clusterIdentifier}
        </#switch>
    </th>
</tr>
<tr>
    <th>Number partitions</th>
    <td>${expectedTopicInfo.properties.partitionCount}</td>
</tr>
<tr>
    <th>Replication factor</th>
    <td>${expectedTopicInfo.properties.replicationFactor}</td>
</tr>
<#list expectedTopicInfo.config as key, value>
    <tr>
        <td>${key}</td>
        <td class="conf-value" data-name="${key}" data-value="${value}">${value}</td>
    </tr>
</#list>
<#if expectedTopicInfo.config?size == 0>
    <tr>
        <td colspan="2">
            <i>(no config overrides of defaults)</i>
        </td>
    </tr>
</#if>
<#if bulkIterateBy??>
    <tr class="missing-topic" data-cluster-identifier="${clusterIdentifier}" data-topic-name="${expectedTopicInfo.name}">
        <td colspan="100">
            <#switch bulkIterateBy>
                <#case "TOPIC">
                    <#assign statusId = "op-status-"+expectedTopicInfo.name>
                    <#break>
                <#case "CLUSTER">
                    <#assign statusId = "op-status-"+clusterIdentifier>
                    <#break>
            </#switch>
            <#include "../common/serverOpStatus.ftl">
        </td>
    </tr>
</#if>

