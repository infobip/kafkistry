<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="reBalanceSuggestion" type="com.infobip.kafkistry.service.topic.ReBalanceSuggestion" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/assignmentsData.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/reAssignTopicPartitionReplicas.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/replicationThrottle.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic re-assignment</title>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1>Topic re-assign with custom assignment</h1>
    <hr>
    <br>

    <#include "reAssignmentMetadata.ftl">

    <#assign maxBrokerIOBytes = reBalanceSuggestion.dataMigration.maxBrokerIOBytes>
    <#include "replicationThrottle.ftl">
    <br/>

    <button id="apply-re-assignments-btn" class="btn btn-info btn-sm" data-topic-name="${topicName}"
            data-cluster-identifier="${clusterInfo.identifier}">
        Apply custom re-assignments
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>