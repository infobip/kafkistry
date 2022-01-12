<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="allCommits" type="java.util.List<com.infobip.kafkistry.service.ChangeCommit<com.infobip.kafkistry.service.Change>>" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Commits history</title>
    <meta name="current-nav" content="nav-history"/>
    <script src="static/gitRefresh.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>

<div class="container">

    <#include "gitRefresh.ftl">

    <div class="card">
        <div class="card-header">
            <span class="h4">All commits history</span>
        </div>

        <div class="card-body pl-0 pr-0">
            <#include "../common/loading.ftl">
            <#assign useDatatable = true>
            <#include "commitsTable.ftl">
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
