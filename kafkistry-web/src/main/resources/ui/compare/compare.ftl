<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="allTopics" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="allClusters" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifiers" type="java.util.List<java.lang.String>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/compare.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Compare topics</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <div class="targets">
        <#if topicName?? && clusterIdentifiers??>
            <#list clusterIdentifiers as clusterIdentifier>
                <#include "subjectForm.ftl">
            </#list>
        <#else>
            <#include "subjectForm.ftl">
            <#include "subjectForm.ftl">
        </#if>
    </div>
    <div class="row">
        <div class="col-1"></div>
        <div class="col p-0">
            <button id="add-target-btn" class="btn btn-outline-primary mt-2 mb-2">Add target...</button>
        </div>
    </div>
    <#include "../common/serverOpStatus.ftl">
</div>

<div id="compare-result"></div>

<div style="display: none;">
    <div id="subject-template">
        <#assign topicName = "">
        <#include "subjectForm.ftl">
    </div>
    <div class="all-topics">
        <#list allTopics as topic>
            <div class="data-topic-name" data-topic="${topic}"></div>
        </#list>
    </div>
</div>
<#include "../common/pageBottom.ftl">
</body>
</html>