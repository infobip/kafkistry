<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="commitChanges" type="com.infobip.kafkistry.repository.storage.CommitFileChanges" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Commit changes</title>
    <meta name="current-nav" content="nav-app-info"/>
    <script src="static/git/diffHighlight.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>

<div class="container">

    <#assign activeNavItem = "git">
    <#include "../about/submenu.ftl">

    <h3><#include "../common/backBtn.ftl"> Commit changes</h3>

    <#assign commit = commitChanges.commit>
    <table class="table table-hover">
        <tr>
            <th>Committer username</th>
            <td>${commit.username}</td>
        </tr>
        <tr>
            <th>Full commit ID</th>
            <td>${commit.commitId}</td>
        </tr>
        <tr>
            <th>Time</th>
            <td class="time" data-time="${(commit.timestampSec * 1000)?c}"></td>
        </tr>
        <tr>
            <th>Message</th>
            <td class="text-links">${commit.message}</td>
        </tr>
    </table>

    <#if commitChanges.files?size == 0>
        <i>No changes in this commit</i>
    </#if>

    <#list commitChanges.files as file>
        <div class="card">
            <div class="card-header">
                <span class="badge ${util.changeTypeClass(file.changeType)}">${file.changeType}</span>
                <strong>File: </strong>
                <span>${file.name}</span>
            </div>
            <div class="card-body p-1">
                <div class="file-content">
                    <pre class="before" style="display: none;">${file.oldContent!""}</pre>
                    <pre class="after" style="display: none;">${file.newContent!""}</pre>
                    <pre class="diff m-0"></pre>
                </div>
            </div>
        </div>
    </#list>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>