<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="existingAssignments" type="java.util.Map<java.lang.Integer, java.util.List<java.lang.Integer>>" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/customReAssignment.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic re-assignment</title>
    <meta name="customAssignBaseUrl"
          content="${appUrl.topicsManagement().showCustomReAssignment(topicName, clusterInfo.identifier, "")}">
    <meta name="brokerIds" content="${clusterInfo.nodeIds?join(",")}">
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1>Topic re-assign with custom assignment</h1>

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
            <th>Brokers</th>
            <td>
                <#include "../clusters/clusterNodesList.ftl">
            </td>
        </tr>
    </table>

    <table class="table table-sm fixed-layout">
        <thead class="thead-dark">
        <tr>
            <th>Partition</th>
            <th>Preferred leader's size</th>
            <th>Current</th>
            <th>New</th>
            <th>Change</th>
        </tr>
        </thead>
        <#list existingAssignments as partition, replicas>
            <tr>
                <th>${partition?c}</th>
                <td>
                    <#if topicReplicas??>
                        <#if (topicReplicas.partitionBrokerReplicas?api.get(partition)?api.get(replicas[0]))??>
                            <#assign replicaInfo = topicReplicas.partitionBrokerReplicas?api.get(partition)?api.get(replicas[0])>
                            ${util.prettyDataSize(replicaInfo.sizeBytes)}
                        <#else>
                            ---
                        </#if>
                    </#if>
                </td>
                <td>
                    <code class="current-replicas" data-partition="${partition?c}"
                          data-replicas="${replicas?join(",")}">
                        ${replicas?join(", ")}
                    </code>
                </td>
                <td>
                    <label>
                        <input class="form-control new-replicas-input" type="text" value="${replicas?join(", ")}"
                               data-partition="${partition?c}">
                    </label>
                </td>
                <td>
                    <pre class="replicas-change" data-partition="${partition?c}"></pre>
                </td>
            </tr>
        </#list>
    </table>

    <a id="continue-url" class="btn btn-info btn-sm" href="#">
        Continue...
    </a>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>