<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicsReBalanceSuggestions" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.ReBalanceSuggestion>" -->
<#-- @ftlvariable name="topicsReBalanceStatuses" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus>" -->
<#-- @ftlvariable name="totalDataMigration" type="com.infobip.kafkistry.service.topic.DataMigration" -->
<#-- @ftlvariable name="clusterTopicsReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->
<#-- @ftlvariable name="selectionLimitedBy" type="java.util.List<com.infobip.kafkistry.service.topic.BulkReAssignmentSuggestion.SelectionLimitedCause>" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/reBalanceTopic.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/reAssignTopicPartitionReplicas.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/assignmentsData.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/replicationThrottle.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic re-assignment</title>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1><#include "../common/backBtn.ftl"> Topic bulk re-balance on kafka</h1>
    <hr>
    <#assign bulkReBalanceDoc = true>
    <#include "doc/bulkTopicReBalanceDoc.ftl">
    <h3>You are about to re-assign multiple topic partition replica to achieve balance</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
    </p>


    <p>
        <#if selectionLimitedBy?size == 0>
            Selection of topic was not limited by any constraints
        <#else>
            Selection of topics was limited by:
            <#list selectionLimitedBy as causeType>
                <span class="badge badge-dark">${causeType}</span>
            </#list>
        </#if>
    </p>


    <#list topicsReBalanceSuggestions as topicName, reBalanceSuggestion>
        <#assign assignmentStatus = topicsReBalanceStatuses[topicName]>
        <#assign topicReplicas = clusterTopicsReplicas[topicName]>
        <div class="card">
            <div class="card-header collapsed" data-target="#topic-${topicName?index}"
                 data-toggle="collapse">
                <div class="float-left">
                    <span class="if-collapsed">▼</span>
                    <span class="if-not-collapsed">△</span>
                    <strong>${topicName?index + 1})</strong>
                    <span>${topicName}</span>
                    <span>
                        <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)}">
                            <button class="btn btn-sm btn-outline-secondary">Inspect 🔍</button>
                        </a>
                    </span>
                </div>
            </div>
            <div id="topic-${topicName?index}" class="card-body p-0 collapse">
                <#include "reAssignmentMetadata.ftl">
            </div>
        </div>
    </#list>

    <#if topicsReBalanceSuggestions?size gt 0>
        <br/>
        <#assign dataMigration = totalDataMigration>
        <table class="table">
            <thead class="thead-dark">
                <tr><th colspan="2" class="text-center">Total data migration</th></tr>
            </thead>
            <#include "assignmentDataMigration.ftl">
        </table>
        <br/>

        <#assign maxBrokerIOBytes = totalDataMigration.maxBrokerIOBytes>
        <#include "replicationThrottle.ftl">

        <br/>
        <button id="apply-bulk-re-assignments-btn" class="btn btn-primary btn-sm"
                data-cluster-identifier="${clusterInfo.identifier}">
            Apply ALL re-assignments (${topicsReBalanceSuggestions?size}) <@info.icon tooltip=doc.applyReBalanceAssignmentsBtn/>
        </button>
    <#else>
        <p><i>No topics to re-balance</i></p>
    </#if>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>