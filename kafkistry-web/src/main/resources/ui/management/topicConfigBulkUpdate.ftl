<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterConfigChanges" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.ConfigValueChange>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/topicConfigUpdate.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/topicConfigBulkUpdate.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster topic config bulk update</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Bulk altering topic config</h1>
    <hr>
    <h3>You are about to update topic on multiple clusters</h3>
    <br>

    <p><strong>Topic</strong>:  <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a></p>

    <p>There are ${clusterConfigChanges?size} cluster(s) on which this topic has wrong config</p>

    <#assign bulkIterateBy = "CLUSTER">
    <table class="table table-hover">
        <#list clusterConfigChanges as clusterIdentifier, configChanges>
            <#include "topicConfigChanges.ftl">
        </#list>
    </table>

    <br/>
    <button id="bulk-update-topic-config-btn" class="btn btn-primary btn-sm" data-topic-name="${topicName}">
        Alter topic's config on clusters (${clusterConfigChanges?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>