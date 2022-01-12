<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="topicInfo" type="com.infobip.kafkistry.service.ExpectedTopicInfo" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/missingTopicCreation.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Missing topic creation</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1>Create missing topic</h1>
    <hr>
    <h3>You are about to create topic that is missing on cluster</h3>
    <br/>

    <table class="table">
        <#assign expectedTopicInfo = topicInfo>
        <#assign clusterIdentifier = cluster.identifier>
        <#include "expectedTopicInfo.ftl">
    </table>

    <br/>
    <button id="create-missing-btn" class="btn btn-primary btn-sm" data-topic-name="${topicInfo.name}"
            data-cluster-identifier="${cluster.identifier}">
        Create topic on cluster
    </button>
    <#include "../common/cancelBtn.ftl">

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>