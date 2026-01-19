<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="branchChanges" type="com.infobip.kafkistry.repository.storage.git.GitRepository.BranchChanges" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Branch changes</title>
    <meta name="current-nav" content="nav-app-info"/>
    <script src="static/git/diffHighlight.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>

<div class="container">

    <#assign activeNavItem = "git">
    <#include "../about/submenu.ftl">

    <h3><#include "../common/backBtn.ftl"> Branch changes against main branch</h3>

    <p><strong>Branch:</strong> <code>${branchChanges.branchName}</code></p>
    <p><strong>Files affected in this branch:</strong></p>
    <#list branchChanges.filesChanges as fileChanges>
        <div class="card">
            <div class="card-header">
                <span class="badge ${util.changeTypeClass(fileChanges.type)}">${fileChanges.type}</span>
                <strong>File: </strong>
                <span>${fileChanges.name}</span>
            </div>
            <div class="card-body p-0 pb-1">
                <table class="table table-hover table-sm">
                    <tr class="table-theme-dark">
                        <th>Type</th>
                        <th>User</th>
                        <th>Commit</th>
                        <th>Message</th>
                    </tr>
                    <#list fileChanges.commitChanges as commitChanges>
                        <tr>
                            <td>
                                <span class="badge ${util.changeTypeClass(commitChanges.type)}">${commitChanges.type}</span>
                            </td>
                            <td>
                                <span>${commitChanges.commit.username}</span>
                            </td>
                            <td>
                                ${util.commitHashUrl(commitChanges.commit.commitId, gitCommitBaseUrl!'', gitEmbeddedBrowse)}
                                <br/>
                                <small class="time" data-time="${(commitChanges.commit.timestampSec*1000)?c}"></small>
                            </td>
                            <td>
                                <div class="p-0">
                                    <pre class="text-sm text-secondary m-0"
                                         style="max-height:200px; white-space: pre-wrap; overflow-y: scroll;"><small
                                                class="text-links git-links">${commitChanges.commit.message}</small></pre>
                                </div>
                            </td>
                        </tr>
                    </#list>
                </table>
                <strong>Total diff</strong> over ${fileChanges.commitChanges?size} commit(s):
                <button class="btn btn-sm btn-outline-secondary collapsed" data-bs-toggle="collapse"
                        data-bs-target=".diff-${fileChanges?index}">
                    <span class="if-collapsed">Expand...</span>
                    <span class="if-not-collapsed">Collapse</span>
                </button>
                <div class="file-content p-1 collapse diff-${fileChanges?index}">
                    <pre class="before" style="display: none;">${fileChanges.oldContent!""}</pre>
                    <pre class="after" style="display: none;">${fileChanges.newContent!""}</pre>
                    <pre class="diff m-0"></pre>
                </div>
            </div>
        </div>
        <br/>
    </#list>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>