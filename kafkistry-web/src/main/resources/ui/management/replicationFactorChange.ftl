<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="replicationFactorChange" type="com.infobip.kafkistry.service.topic.PartitionPropertyChange" -->
<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/replicationFactorChange.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/assignmentsData.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/replicationThrottle.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic re-assignment</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Topic replication factor change on kafka</h1>
    <hr>
    <h3>You are about to change topic replication factor by assigning new replicas on kafka cluster</h3>
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
            <th>Replication factor before</th>
            <td>${replicationFactorChange.currentAssignments?first.replicasAssignments?size}</td>
        </tr>
        <tr>
            <th>Replication factor after</th>
            <td>${(replicationFactorChange.change.newAssignments?api.get(replicationFactorChange.change.newAssignments?keys[0])?size)!"---"}</td>
        </tr>
        <#if replicationFactorChange.dataMigration??>
            <#assign dataMigration = replicationFactorChange.dataMigration>
            <#include "assignmentChangeStats.ftl">
        </#if>
    </table>

    <#assign partitionsAssignments = replicationFactorChange.currentAssignments>
    <#assign partitionChange = replicationFactorChange>
    <#include "../topics/partitionReplicaAssignments.ftl">

    <#switch replicationFactorChange.type>
        <#case "IMPOSSIBLE">
            <div class="alert alert-danger">
                <strong>WARNING</strong>: Partition change is not possible, reason: ${replicationFactorChange.impossibleReason}
            </div>
            <#break>
        <#case "NOTHING">
            <div class="alert alert-primary">
                <strong>NOTE</strong>: Partition change is not needed because expected and actual values are the same
            </div>
            <#break>
        <#case "CHANGE">
            <#assign assignments = replicationFactorChange.change.newAssignments>
            <#include "assignmentData.ftl">
            <#if replicationFactorChange.change.addedPartitionReplicas?size gt 0>
                <#assign firstPartitionId = replicationFactorChange.change.addedPartitionReplicas?keys[0]>
                <#assign replicationFactorInc = replicationFactorChange.change.addedPartitionReplicas?api.get(firstPartitionId)>
                <#assign maxBrokerIOBytes = replicationFactorChange.dataMigration.maxBrokerIOBytes>
                <#include "replicationThrottle.ftl">
                <br/>
                <button id="assign-new-replicas-btn" class="btn btn-primary btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${clusterInfo.identifier}">
                    Assign new topic partition replicas on cluster (+${replicationFactorInc?size})
                </button>
            <#else>
                <#assign firstPartitionId = replicationFactorChange.change.removedPartitionReplicas?keys[0]>
                <#assign replicationFactorDec = replicationFactorChange.change.removedPartitionReplicas?api.get(firstPartitionId)>
                <button id="remove-replicas-btn" class="btn btn-danger btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${clusterInfo.identifier}">
                    Remove topic partition replicas on cluster (-${replicationFactorDec?size})
                </button>
            </#if>
            <#break>
    </#switch>
    <#include "../common/cancelBtn.ftl">
    <p>
        <strong>NOTE</strong>: Don't forget to do (<strong>Verify re-assignments</strong>) after addition of new replicas completes
    </p>
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
