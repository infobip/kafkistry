<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="partitionCountChange" type="com.infobip.kafkistry.service.topic.PartitionPropertyChange" -->
<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/partitionCountChange.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/assignmentsData.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic partitions change</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Topic partition count change on kafka</h1>
    <hr>
    <h3>You are about to change topic partition count on kafka cluster</h3>
    <br>

    <table class="table">
        <tr>
            <th>Cluster</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterInfo.identifier)}">${clusterInfo.identifier}</a>
            </td>
        </tr>
        <tr>
            <th>Topic</th>
            <td>
                <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
            </td>
        </tr>
        <tr>
            <th>Partition count before</th>
            <td>${partitionCountChange.currentAssignments?size}</td>
        </tr>
        <tr>
            <th>Partition count after</th>
            <td>${(partitionCountChange.change.newAssignments?size)!"---"}</td>
        </tr>
        <#if partitionCountChange.dataMigration??>
            <#assign dataMigration = partitionCountChange.dataMigration>
            <#include "assignmentChangeStats.ftl">
        </#if>
    </table>

    <#assign partitionsAssignments = partitionCountChange.currentAssignments>
    <#assign partitionChange = partitionCountChange>
    <#include "../topics/partitionReplicaAssignments.ftl">

    <#switch partitionCountChange.type>
        <#case "IMPOSSIBLE">
            <div class="alert alert-danger">
                <strong>WARNING</strong>: Partition change is not possible, reason: ${partitionCountChange.impossibleReason}
                <br/>
                <#assign url = appUrl.topicsManagement().showDeleteTopicOnCluster(topicName, clusterInfo.identifier)>
                <a href="${url}"><button class="text-nowrap btn btn-outline-danger btn-sm m-1">Force delete topic on kafka...</button></a>
            </div>
            <#break>
        <#case "NOTHING">
            <div class="alert alert-primary">
                <strong>NOTE</strong>: Partition change is not needed because expected and actual values are the same
            </div>
            <#break>
        <#case "CHANGE">
            <#assign assignments = partitionCountChange.change.addedPartitionReplicas>
            <#include "assignmentData.ftl">
            <#assign numNewPartitions = partitionCountChange.change.addedPartitionReplicas?size>
            <button id="add-partitions-btn" class="btn btn-primary btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${clusterInfo.identifier}">
                Add new topic partitions on cluster (+${numNewPartitions})
            </button>
            <#break>
    </#switch>
    <#include "../common/cancelBtn.ftl">
    <br/>
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
