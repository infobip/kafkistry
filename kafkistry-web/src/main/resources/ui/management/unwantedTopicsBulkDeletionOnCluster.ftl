<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="unwantedTopics" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="topicsConsumerGroups" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.consumers.KafkaConsumerGroup>" -->
<#-- @ftlvariable name="topicsTopicOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/unwantedTopicsBulkDeletion.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Unwanted topics bulk deletion</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1>Unwanted topics bulk deletion</h1>
    <hr>
    <h3>You are about to delete multiple topics from cluster</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
    </p>
    <p>There are ${unwantedTopics?size} topics(s) on cluster which will be deleted</p>

    <div class="alert alert-danger">
        <strong>WARNING</strong>: <span style="color: red;">this operation causes data loss</span>
    </div>

    <#assign bulkIterateBy = "TOPIC">
    <table class="table">
        <#list unwantedTopics as topicName>
            <#assign topicConsumerGroups = topicsConsumerGroups[topicName]>
            <#if (topicsTopicOffsets[topicName])??>
                <#assign topicOffsets = topicsTopicOffsets[topicName]>
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

    <button id="bulk-delete-unwanted-topics-btn" class="btn btn-danger btn-sm" data-cluster-identifier="${clusterIdentifier}">
        Delete unwanted topics on cluster (${unwantedTopics?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
