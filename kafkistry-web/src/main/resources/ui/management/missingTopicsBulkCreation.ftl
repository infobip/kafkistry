<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="missingTopics" type="java.util.List<com.infobip.kafkistry.service.ExpectedTopicInfo>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/missingTopicsBulkCreation.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Missing topics bulk creation</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1>Missing topics bulk creation</h1>
    <hr>
    <h3>You are about to create all missing topics on cluster</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(cluster.identifier)}">${cluster.identifier}</a>
    </p>

    <p>There are ${missingTopics?size} missing topic(s) to create</p>

    <#assign clusterIdentifier = cluster.identifier>
    <#assign bulkIterateBy = "TOPIC">
    <table class="table">
        <#list missingTopics as expectedTopicInfo>
            <#include "expectedTopicInfo.ftl">
        </#list>
    </table>

    <br/>
    <button id="bulk-create-missing-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${cluster.identifier}">
        Create all missing on cluster (${missingTopics?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
