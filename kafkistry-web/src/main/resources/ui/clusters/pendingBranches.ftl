<#-- @ftlvariable name="pendingBranches"  type="java.util.List<com.infobip.kafkistry.service.history.BranchRequests<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="gitBranchBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->

<#import "../common/infoIcon.ftl" as hInfo>

<div class="card">
    <div class="card-header">
        <h5>Pending branches</h5>
    </div>
    <div class="card-body p-0">
        <#if pendingBranches?size == 0>
            <i>(no pending changes)</i>
        <#else>
            <table class="table table-sm mb-0">
                <thead class="thead-dark">
                <tr>
                    <th>Branch</th>
                    <th>Clusters</th>
                    <th>Commit</th>
                    <th></th>
                </tr>
                </thead>
                <#list pendingBranches as branchChanges>
                    <tr>
                        <td>
                            <#if gitBranchBaseUrl??>
                                <#assign url = gitBranchBaseUrl + branchChanges.branch?url>
                                <a target="_blank" href="${url}">${branchChanges.branch}</a>
                            <#elseif gitEmbeddedBrowse>
                                <a href="${appUrl.git().showBranch(branchChanges.branch)}">${branchChanges.branch}</a>
                            <#else>
                                branch '${branchChanges.branch}'
                            </#if>
                        </td>
                        <td>
                            <#list branchChanges.requests as clusterRequest>
                            <a href="${appUrl.clusters().showCluster(clusterRequest.identifier)}">
                                ${clusterRequest.identifier}</a><#if !clusterRequest?is_last>, </#if>
                            </#list>
                        </td>
                        <td>
                            <#list branchChanges.commits as commitChanges>
                                <#assign commit = commitChanges.commit>
                                <#assign tooltipHtml>
                                    ${commit.username}
                                    <br/>
                                    ${(commit.timestampSec*1000)?number_to_datetime} UTC
                                    <p class='text-light'>${commit.message}</p>
                                </#assign>
                                <span data-username="${commit.username}" data-time-sec="${commit.timestampSec?c}">
                                    <@hInfo.icon tooltip=tooltipHtml/>
                                    <#if gitCommitBaseUrl??>
                                        <#assign commitUrl = gitCommitBaseUrl + commit.commitId>
                                        <a <#if !gitEmbeddedBrowse>target="_blank"</#if>
                                           href="${commitUrl}">${commit.commitId?substring(0, 6)}</a><#if !commitChanges?is_last>, </#if>
                                    <#else>
                                        ${commit.commitId?substring(0, 6)}<#if !commitChanges?is_last>, </#if>
                                    </#if>
                                </span>
                            </#list>
                        </td>
                        <td>
                            <a href="${appUrl.clusters().showTagsOnBranch(branchChanges.branch)}"
                               class="btn btn-sm btn-outline-primary">
                                Edit from...
                            </a>
                        </td>
                    </tr>
                </#list>
            </table>
        </#if>
    </div>
</div>
