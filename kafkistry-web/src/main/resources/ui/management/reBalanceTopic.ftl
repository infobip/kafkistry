<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="reBalanceSuggestion" type="com.infobip.kafkistry.service.ReBalanceSuggestion" -->
<#-- @ftlvariable name="reBalanceMode" type="com.infobip.kafkistry.service.ReBalanceMode" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos" -->

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
    <h1>Topic re-balance on kafka</h1>
    <hr>
    <h3>You are about to re-assign topic partition replica to achieve balance</h3>
    <#include "doc/bulkTopicReBalanceDoc.ftl">
    <br>

    <div>
        <p>
            <#assign clusterIdentifier = clusterInfo.identifier>
            Re-balance mode:
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "REPLICAS")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "REPLICAS">active</#if>">
                Only replicas
            </button>
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "LEADERS")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "LEADERS">active</#if>">
                Only leaders
            </button>
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "REPLICAS_THEN_LEADERS")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "REPLICAS_THEN_LEADERS">active</#if>">
                Replicas then leaders
            </button>
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "LEADERS_THEN_REPLICAS")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "LEADERS_THEN_REPLICAS">active</#if>">
                Leaders then replicas
            </button>
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "ROUND_ROBIN")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "ROUND_ROBIN">active</#if>">
                Round robin
            </button>
            <button data-href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "RANDOM")}"
                    class="rebalance-nav-item btn btn-outline-primary <#if reBalanceMode.name() == "RANDOM">active</#if>">
                Random
            </button>
        </p>
    </div>

    <#include "reAssignmentMetadata.ftl">

    <#assign maxBrokerIOBytes = reBalanceSuggestion.dataMigration.maxBrokerIOBytes>
    <#include "replicationThrottle.ftl">

    <br/>
    <button id="apply-re-assignments-btn" class="btn btn-primary btn-sm" data-topic-name="${topicName}"
            data-cluster-identifier="${clusterInfo.identifier}">
        Apply re-assignments <@info.icon tooltip=doc.applyReBalanceAssignmentsBtn/>
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>