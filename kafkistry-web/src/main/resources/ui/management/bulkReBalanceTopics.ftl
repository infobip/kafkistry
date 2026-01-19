<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="stats" type="com.infobip.kafkistry.service.topic.BulkReAssignmentSuggestion.SuggestionStats" -->
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

    <h4>Selection pipeline:</h4>
    <div class="row">
        <div class="col text-center"><span class="badge bg-primary p-2">ALL</span><br/>${stats.counts.all}</div>
        <div class="col-auto h4">&rarr;</div>
        <div class="col text-center"><span class="badge bg-danger p-2">FILTERED</span><br/>${stats.counts.filtered}</div>
        <div class="col-auto h4">&rarr;</div>
        <div class="col text-center"><span class="badge bg-info p-2">QUALIFIED</span><br/>${stats.counts.qualified}</div>
        <div class="col-auto h4">&rarr;</div>
        <div class="col text-center"><span class="badge bg-warning p-2">CANDIDATES</span><br/>${stats.counts.candidates}</div>
        <div class="col-auto h4">&rarr;</div>
        <div class="col text-center"><span class="badge bg-success p-2">SELECTED</span><br/>${stats.counts.selected}</div>
    </div>
    <br/>
    <p>
        <#if selectionLimitedBy?size == 0>
            Selection of topic was not limited by any constraints
        <#else>
            Selection of topics was limited by:
            <#list selectionLimitedBy as causeType>
                <span class="badge bg-neutral">${causeType}</span>
            </#list>
        </#if>
        <button role="button" data-bs-toggle="collapse" data-bs-target="#pipeline-selection-details"
                class="btn btn-sm btn-secondary mx-2">
            Pipeline selection details...
        </button>
    </p>

    <div id="pipeline-selection-details" class="collapse">
        <#macro filterStageDisplay filterStages>
        <#-- @ftlvariable name="filterStages" type="java.util.List<com.infobip.kafkistry.service.topic.BulkReAssignmentSuggestion.TopicSuggestStage>" -->
            <table class="table table-hover table-sm">
                <tr class="table-theme-dark">
                    <th>#</th>
                    <th>Topic</th>
                    <th>Outcome</th>
                    <th>Explanation</th>
                </tr>
                <#list filterStages as filterStage>
                    <tr>
                        <td>${filterStage?index + 1})</td>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(filterStage.topic, clusterIdentifier)}">
                                ${filterStage.topic}
                            </a>
                        </td>
                        <td>
                            <#if filterStage.passed>
                                <span class="badge bg-success">PASSED</span>
                            <#else>
                                <span class="badge bg-danger">DROPPED</span>
                            </#if>
                        </td>
                        <td>
                            <#list filterStage.explanations as explanation>
                                <span class="small <#if filterStage.passed>text-success<#else>text-danger</#if>">
                                    ${explanation}
                                </span>
                                <#if explanation?has_next>/</#if>
                            </#list>
                        </td>
                    </tr>
                </#list>
                <#if filterStages?size == 0>
                    <tr><td colspan="100"><i>(no topics)</i></td></tr>
                </#if>
            </table>
        </#macro>
        <div class="card">
            <div class="card-header h4">Filter stage</div>
            <div class="card-body overflow-auto p-0" style="max-height: 60vh;">
                <@filterStageDisplay filterStages=stats.filter/>
            </div>
        </div>
        <br/>
        <div class="card">
            <div class="card-header h4">Qualify stage</div>
            <div class="card-body overflow-auto p-0" style="max-height: 60vh;">
                <@filterStageDisplay filterStages=stats.qualify/>
            </div>
        </div>
        <br/>
        <div class="card">
            <div class="card-header h4">Candidates stage</div>
            <div class="card-body overflow-auto p-0" style="max-height: 60vh;">
                <@filterStageDisplay filterStages=stats.candidate/>
            </div>
        </div>
        <br/>
        <div class="card">
            <div class="card-header h4">Constraints stage</div>
            <div class="card-body overflow-auto p-0" style="max-height: 60vh;">
                <@filterStageDisplay filterStages=stats.constraints/>
            </div>
        </div>
        <br/>
    </div>
    <br/>


    <#list topicsReBalanceSuggestions as topicName, reBalanceSuggestion>
        <#assign assignmentStatus = topicsReBalanceStatuses[topicName]>
        <#assign topicReplicas = clusterTopicsReplicas[topicName]>
        <div class="card">
            <div class="card-header collapsed" data-bs-target="#topic-${topicName?index}"
                 data-bs-toggle="collapse">
                <div class="float-start">
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
        <table class="table table-hover">
            <thead class="table-theme-dark">
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