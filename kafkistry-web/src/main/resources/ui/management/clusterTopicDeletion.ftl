<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicConsumerGroups" type="java.util.List<com.infobip.kafkistry.service.consumers.KafkaConsumerGroup>" -->
<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/clusterTopicDeletion.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster topic deletion</title>
</head>

<body>


<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumerUtil>

<div class="container">

    <h1>Delete actual topic on cluster</h1>
    <hr>
    <h3>You are about to delete topic on kafka cluster</h3>
    <br>

    <div class="alert alert-danger">
        <strong>WARNING</strong>: <span style="color: red;">this operation causes data loss</span>
    </div>

    <#assign clusterIdentifier = clusterInfo.identifier>
    <table class="table">
        <#include "unwantedTopicInfo.ftl">
    </table>

    <div class="card">
        <div class="card-header"><h5>Sanity check</h5></div>
        <div class="card-body">
            <p><label class="mouse-pointer">Delete even if it is configured to exist (force delete): <input type="checkbox" id="force-delete"></label></p>
            <p><label>Enter word DELETE to confirm what you are about to do <input type="text" id="delete-confirm"></label></p>
        </div>
    </div>
    <br/>

    <button id="delete-topic-btn" class="btn btn-danger btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${clusterInfo.identifier}">
        Delete topic on cluster
    </button>
    <#include "../common/cancelBtn.ftl">

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>