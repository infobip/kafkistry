<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="recordsStructure" type="com.infobip.kafkistry.model.RecordsStructure" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/recordsStructure-js/recordsStructure.js?ver=${lastCommit}"></script>
    <link rel="stylesheet" href="static/css/recordStructure.css?ver=${lastCommit}">
    <title>Kafkistry: Records Structure</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<#import "structureElements.ftl" as structure>

<div class="container">
    <h3><#include "../common/backBtn.ftl"> Records structure of topic</h3>

    <table class="table table-hover">
        <tr>
            <th>Topic</th>
            <td>
                <#if clusterIdentifier??>
                    <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)}">
                        ${topicName} @ ${clusterIdentifier}
                    </a>
                <#else>
                    <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
                </#if>
            </td>
        </tr>
        <tr>
            <th>Cluster</th>
            <td>
                <#if clusterIdentifier??>
                    <span class="badge bg-neutral">CLUSTER</span>
                    <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
                <#else>
                    <span class="badge bg-primary">ALL COMBINED</span>
                </#if>
            </td>
        </tr>
        <tr>
            <th>Utilities</th>
            <td>
                <a href="${appUrl.consumeRecords().showConsumePage(topicName, clusterIdentifier!'')}">
                    <button class="btn btn-outline-info btn-sm">Consume...</button>
                </a>
            </td>
        </tr>
    </table>
    <hr/>

    <#if !recordsStructure??>
        <div class="alert alert-danger">
            Not having analyzed structure for topic: '${topicName}'
            <#if clusterIdentifier??>on cluster '${clusterIdentifier}'</#if>
        </div>
    <#else>
        <@structure.showStructure structure=recordsStructure/>
    </#if>
</div>

<#--spacer for collapse not caausing scroll-->
<div style="height: 80%;"></div>

<#include "../common/pageBottom.ftl">
</body>
</html>
