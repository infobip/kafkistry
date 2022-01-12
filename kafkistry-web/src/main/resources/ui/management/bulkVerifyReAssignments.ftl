<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicsToVerifyReAssignments" type="java.util.List<java.lang.String>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/verifyReAssignments.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Verify topics re-assignments</title>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h1><#include "../common/backBtn.ftl"> Verify topics re-assignments</h1>
    <hr>
    <h3>You are about to verify all topics on cluster that need verification of re-assignments</h3>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
    </p>

    <p>There are ${topicsToVerifyReAssignments?size} topic(s) that need re-assignment verification</p>

    <#list topicsToVerifyReAssignments as topic>
        <div class="verify-re-assignment-topic" data-topic-name="${topic}">
            <p>
                <strong>Topic: </strong>
                <span class="name">
                    <a href="${appUrl.topics().showTopic(topic)}">${topic}</a>
                </span>
                <#assign statusId = "op-status-"+topic>
                <#include "../common/serverOpStatus.ftl">
            </p>
        </div>
    </#list>
    <br/>
    <button id="bulk-verify-re-assignments-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${clusterIdentifier}">
        Execute verify re-assignments on cluster (${topicsToVerifyReAssignments?size})
    </button>
    <#include "../common/cancelBtn.ftl">

    <br/>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
