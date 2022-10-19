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
        <tr>
            <th>Cluster</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
            </td>
        </tr>
        <tr>
            <th>Topic</th>
            <td>
                <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
            </td>
        </tr>
        <tr>
            <th>Producing</th>
            <td>
                <#if topicOffsets??>
                    <#include "../topics/topicOffsetsStatus.ftl">
                <#else>
                    <i>N/A</i>
                </#if>
            </td>
        </tr>
        <tr>
            <th>Consumers</th>
            <td>
                <#if topicConsumerGroups?size == 0>
                    <i>(no consumer groups reading from this topic)</i>
                <#else>
                    <table class="table table-sm mb-0">
                        <thead class="thead-dark">
                        <tr>
                            <th style="width: 60%">Group</th>
                            <th>Status</th>
                            <th>Lag</th>
                        </tr>
                        </thead>
                        <#list topicConsumerGroups as consumerGroup>
                            <tr>
                                <td>
                                    <a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroup.groupId, topicName)}">
                                        ${consumerGroup.groupId}
                                    </a>
                                </td>
                                <td>
                                    <@util.namedTypeStatusAlert type=consumerGroup.status/>
                                </td>
                                <#list consumerGroup.topicMembers as topicMember>
                                    <#if topicMember.topicName == topicName>
                                        <td>
                                            <@util.namedTypeStatusAlert type=topicMember.lag.status/>
                                        </td>
                                    </#if>
                                </#list>
                            </tr>
                        </#list>
                    </table>
                </#if>

            </td>
        </tr>
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