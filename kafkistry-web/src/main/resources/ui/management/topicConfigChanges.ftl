<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="configChanges" type="java.util.List<com.infobip.kafkistry.service.topic.ConfigValueChange>" -->

<#-- @ftlvariable name="bulkIterateBy" type="java.lang.String" -->

<#import "../common/util.ftl" as ccUtil>

<tr class="table-theme-dark">
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
<tr class="table-theme-accent">
    <th>Key</th>
    <th>Current value</th>
    <th>New value</th>
</tr>
<#list configChanges as configChange>
    <tr data-topic="${topicName}" data-cluster="${clusterIdentifier}">
        <td>${configChange.key}</td>
        <td class="conf-value old-value" data-name="${configChange.key}" data-value="${configChange.oldValue!''}"
            data-is-null="${(configChange.oldValue??)?then("false", "true")}" style="word-break: break-word;">
            <#if configChange.oldValue??>
                <#if configChange.oldValue == "">
                    <i>(empty)</i>
                <#else>
                    ${configChange.oldValue}
                </#if>
            <#else>
                <span class="text-primary">null</span>
            </#if>
        </td>
        <td class="conf-value new-value" data-name="${configChange.key}" data-value="${configChange.newValue!''}"
            data-is-null="${(configChange.newValue??)?then("false", "true")}">
            <#if configChange.newValue??>
                <#if configChange.newValue == "">
                    <i>(empty)</i>
                <#else>
                    ${configChange.newValue}
                </#if>
            <#else>
                <span class="text-primary">null</span>
            </#if>
            <#if configChange.newToDefault>
                <i>(cluster's default)</i>
            </#if>
        </td>
    </tr>
</#list>
<#if configChanges?size == 0>
    <tr>
        <td colspan="100">
            <i>(nothing)</i>
        </td>
    </tr>
</#if>
<#if bulkIterateBy??>
    <tr class="wrong-config-topic" data-cluster-identifier="${clusterIdentifier}" data-topic-name="${topicName}">
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

