<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterExpectedTopicInfos" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.ExpectedTopicInfo>" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/missingTopicBulkCreationOnClusters.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Missing topic bulk creation</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1>Missing topic bulk creation</h1>
    <hr>
    <h3>You are about to create topic on multiple clusters</h3>
    <br>

    <p><strong>Topic</strong>: <a href="${appUrl.topics().showTopic(topic.name)}">${topic.name}</a>
    </p>

    <p>There are ${clusterExpectedTopicInfos?size} cluster(s) on which this topic is missing</p>

    <#assign bulkIterateBy = "CLUSTER">
    <table class="table">
        <#list clusterExpectedTopicInfos as clusterIdentifier, expectedTopicInfo>
            <#include "expectedTopicInfo.ftl">
        </#list>
    </table>

    <br/>
    <button id="bulk-create-where-missing-btn" class="btn btn-primary btn-sm" data-topic-name="${topic.name}">
        Create missing topic on clusters (${clusterExpectedTopicInfos?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
