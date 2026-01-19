<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="configChanges" type="java.util.List<com.infobip.kafkistry.service.topic.ConfigValueChange>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/topicConfigUpdate.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster topic config update</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Updating topic config on cluster</h1>
    <hr>
    <h3>You are about to update topic's config on kafka cluster</h3>
    <br>

    <table class="table table-hover">
        <#assign clusterIdentifier = cluster.identifier>
        <#include "topicConfigChanges.ftl">
    </table>

    <br/>
    <button id="update-topic-config-btn" class="btn btn-primary btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${cluster.identifier}">
        Alter topic's config on cluster
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#include "../common/serverOpStatus.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>