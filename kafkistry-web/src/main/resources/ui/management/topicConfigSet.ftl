<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="topicConfig" type="java.util.Map<java.lang.String, com.infobip.kafkistry.kafka.ConfigValue>" -->
<#-- @ftlvariable name="topicConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/topicConfigSet.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic config</title>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1><#include "../common/backBtn.ftl"> Topic manually set config on topic</h1>

    <table class="table">
        <tr>
            <th>Cluster</th>
            <td>
                <a href="${appUrl.clusters().showCluster(cluster.identifier)}">${cluster.identifier}</a>
            </td>
        </tr>
        <tr>
            <th>Topic</th>
            <td>
                <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
            </td>
        </tr>
    </table>

    <table class="table table-sm fixed-layout">
        <thead class="thead-dark">
        <tr>
            <th>Config property</th>
            <th>Current value</th>
            <th>New value</th>
            <th>Change</th>
        </tr>
        </thead>
        <#list topicConfig as configKey, configValue>
            <tr>
                <td style="overflow-x: auto;">${configKey} <@info.icon tooltip=(topicConfigDoc[configKey]?replace('"', "'"))!''/></td>
                <td class="conf-value" data-name="${configKey}" data-value="${configValue.value}"
                    style="max-width: 380px;" title="${configValue.source}">
                    <#if configValue.value??>
                        <div style="max-width: 450px; overflow-x: auto;">${configValue.value}</div>
                    <#else>
                        <pre class="text-primary">null</pre>
                    </#if>
                </td>
                <td>
                    <label class="width-full">
                        <input class="width-full conf-value-in" type="text" name="${configKey}" value="${configValue.value!''}" data-callback="refreshDiff">
                        <span class="small text-primary conf-value-out"></span>
                    </label>
                </td>
                <td>
                    <pre class="config-change" data-name="${configKey}"></pre>
                </td>
            </tr>
        </#list>
    </table>

    <button id="topic-config-set-btn" class="btn btn-info btn-sm"
            data-clusterIdentifier="${cluster.identifier}" data-topic="${topicName}">
        Update changed values
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>