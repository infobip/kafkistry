<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="topicsConfigChanges" type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.topic.ConfigValueChange>>" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/topicConfigUpdate.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/bulkTopicsConfigsUpdates.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topics config updates</title>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1><#include "../common/backBtn.ftl"> Bulk altering topics configs</h1>
    <hr>
    <h3>You are about to update config of multiple topics on cluster</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(cluster.identifier)}">${cluster.identifier}</a></p>

    <p>There are ${topicsConfigChanges?size} topics(s) that have wrong config on this cluster</p>

    <#assign bulkIterateBy = "TOPIC">
    <#assign clusterIdentifier = cluster.identifier>
    <table class="table table-hover">
        <#list topicsConfigChanges as topicName, configChanges>
            <#include "topicConfigChanges.ftl">
        </#list>
    </table>

    <br/>
    <button id="bulk-update-topics-configs-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${cluster.identifier}">
        Alter config of cluster's topics (${topicsConfigChanges?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>