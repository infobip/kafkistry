<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="allCommits" type="java.util.List<com.infobip.kafkistry.service.history.ChangeCommit<com.infobip.kafkistry.service.history.Change>>" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Commits history</title>
    <meta name="current-nav" content="nav-app-info"/>
    <script src="static/gitRefresh.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>

<div class="container">

    <#assign activeNavItem = "git">
    <#include "../about/submenu.ftl">

    <#include "gitRefresh.ftl">

    <div class="card">
        <div class="card-header">
            <div class="form-row">
                <div class="col">
                    <span class="h4">Recent history</span>
                </div>
                <#assign lastCounts = [5, 10, 25, 100]>
                <div class="col text-right">
                    Show latest:
                    <#list lastCounts as count>
                        <a class="small btn btn-sm btn-outline-secondary" href="${appUrl.history().showRecentCount(count)}">
                            ${count}
                        </a>
                    </#list>
                </div>
            </div>
        </div>

        <div class="card-body p-0">
            <#assign useDatatable = false>
            <#include "commitsTable.ftl">
        </div>

    </div>

    <br/>
    <#if allCommits?size gt 0>
        <a href="${appUrl.history().showAll()}" >
            <button class="btn btn-primary width-full" data-toggle="collapse" data-target="#opening">
                Show full history... <i id="opening" class="collapse">Opening...</i>
            </button>
        </a>
    </#if>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
