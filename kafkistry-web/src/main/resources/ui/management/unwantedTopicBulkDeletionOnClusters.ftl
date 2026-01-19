<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="unwantedTopicClusters" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="clusterConsumerGroups" type="java.util.Map<com.infobip.kafkistry.service.consumers.KafkaConsumerGroup>" -->
<#-- @ftlvariable name="clusterTopicOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/unwantedTopicsBulkDeletion.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Unwanted topic bulk deletion</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1>Unwanted topic bulk deletion</h1>
    <hr>
    <h3>You are about to delete topic from multiple clusters</h3>
    <br>

    <p><strong>Topic</strong>: <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
    </p>
    <p>There are ${unwantedTopicClusters?size} cluster(s) on which this topic will be deleted</p>

    <div class="alert alert-danger">
        <strong>WARNING</strong>: <span style="color: red;">this operation causes data loss</span>
    </div>

    <#assign bulkIterateBy = "CLUSTER">
    <table class="table table-hover">
        <#list unwantedTopicClusters as clusterIdentifier>
            <#assign topicConsumerGroups = clusterConsumerGroups[clusterIdentifier]>
            <#if (clusterTopicOffsets[clusterIdentifier])??>
                <#assign topicOffsets = clusterTopicOffsets[clusterIdentifier]>
            <#else>
                <#assign topicOffsets = "">
            </#if>
            <#include "unwantedTopicInfo.ftl">
        </#list>
    </table>
    <br/>

    <div class="card">
        <div class="card-header"><h5>Sanity check</h5></div>
        <div class="card-body">
            <p><label>Enter word DELETE to confirm what you are about to do <input type="text" id="bulk-delete-confirm"></label></p>
        </div>
    </div>
    <br/>

    <button id="bulk-delete-where-unwanted-btn" class="btn btn-danger btn-sm" data-topic-name="${topicName}">
        Delete unwanted topic on clusters (${unwantedTopicClusters?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
