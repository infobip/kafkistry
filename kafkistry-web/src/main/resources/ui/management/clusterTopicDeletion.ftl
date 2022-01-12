<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/clusterTopicDeletion.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster topic deletion</title>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">

    <h1>Delete actual topic on cluster</h1>
    <hr>
    <h3>You are about to delete topic on kafka cluster</h3>
    <br>
    <p><strong>WARNING</strong>: <span style="color: red;">this operation causes data loss</span></p>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(clusterInfo.identifier)}">${clusterInfo.identifier}</a></p>
    <p><strong>Topic</strong>:  <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a></p>

    <br/>
    <p><label>Delete even if it is configured to exist (force delete): <input type="checkbox" id="force-delete"></label></p>
    <br/>
    <p><label>Enter word DELETE to confirm what you are about to do <input type="text" id="delete-confirm"></label></p>
    <button id="delete-topic-btn" class="btn btn-danger btn-sm" data-topic-name="${topicName}" data-cluster-identifier="${clusterInfo.identifier}">
        Delete topic on cluster
    </button>
    <#include "../common/cancelBtn.ftl">

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>