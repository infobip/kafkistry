<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="topicsPartitions" type="java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/runPreferredReplicaElection.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Re-elect topics leader partitions</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1><#include "../common/backBtn.ftl"> Topics partition replicas leaders election</h1>
    <hr>
    <h3>You are about to re-elect preferred replica leaders of topics on cluster</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(cluster.identifier)}">${cluster.identifier}</a>
    </p>

    <p>There are ${topicsPartitions?size} topic(s) that need leader re-election</p>

    <#list topicsPartitions as topic, partitions>
        <div class="re-elect-topic" data-topic-name="${topic}">
            <p>
                <strong>Topic: </strong>
                <span class="name">
                    <a href="${appUrl.topics().showTopic(topic)}">${topic}</a>
                </span>
                <strong>Partitions to re-elect:</strong>
                <span>${partitions?join(", ")}</span>
                <#assign statusId = "op-status-"+topic>
                <#include "../common/serverOpStatus.ftl">
            </p>
        </div>
    </#list>
    <br/>
    <button id="bulk-run-preferred-replica-election-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${cluster.identifier}">
        Execute preferred replica leaders on cluster (${topicsPartitions?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
