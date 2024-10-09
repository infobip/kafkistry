<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitBranchBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->

<#-- @ftlvariable name="branch" type="java.lang.String" -->
<#-- @ftlvariable name="clustersDiskUsage" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.ClusterDiskUsage>>" -->
<#-- @ftlvariable name="clustersDiskUsageOnBranch" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.ClusterDiskUsage>>" -->
<#-- @ftlvariable name="clustersDiskUsageDiffs" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.ClusterDiskUsage>>" -->
<#-- @ftlvariable name="clustersBranches" type="java.util.List<com.infobip.kafkistry.service.history.BranchRequests<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="topicsBranches" type="java.util.List<com.infobip.kafkistry.service.history.BranchRequests<com.infobip.kafkistry.service.history.TopicRequest>>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <title>Kafkistry: Clusters resources</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../../commonMenu.ftl">
<#import "../../common/util.ftl" as util>
<#import "../../common/infoIcon.ftl" as info>


<div class="container">
    <h3><#include "../../common/backBtn.ftl"> All Clusters resources</h3>
    <br/>

    <#macro branchCommits branchRequests>
        <#-- @ftlvariable name="branchRequests" type="com.infobip.kafkistry.service.history.BranchRequests<? extends com.infobip.kafkistry.service.history.PendingRequest>" -->
        <#list branchRequests.commits as commitChanges>
            <#assign commit = commitChanges.commit>
            <#assign tooltipHtml>
                ${commit.username}
                <br/>
                ${(commit.timestampSec*1000)?number_to_datetime} UTC
                <p class='text-light'>${commit.message}</p>
            </#assign>
            <span data-username="${commit.username}" data-time-sec="${commit.timestampSec?c}">
                <@info.icon tooltip=tooltipHtml/>
                <#if gitCommitBaseUrl??>
                    <#assign commitUrl = gitCommitBaseUrl + commit.commitId>
                    <a <#if !gitEmbeddedBrowse>target="_blank"</#if>
                       href="${commitUrl}">${commit.commitId?substring(0, 6)}</a><#if !commitChanges?is_last>, </#if>
                <#else>
                    ${commit.commitId?substring(0, 6)}<#if !commitChanges?is_last>, </#if>
                </#if>
            </span>
        </#list>
    </#macro>

    <#if gitStorageEnabled>
        <div>
            <p>Branch selection:</p>
            <ul>
                <li>
                    <#if !(branch??)>
                        <span class="badge badge-primary">SELECTED</span>
                    </#if>
                    <a href="${appUrl.clusters().showClusterResourcesInspect()}">main branch</a>
                </li>
                <#list topicsBranches as topicsBranch>
                    <#assign url = appUrl.clusters().showClusterResourcesInspectOnBranch(topicsBranch.branch)>
                    <li>
                        <#if topicsBranch.branch == (branch!'')>
                            <span class="badge badge-primary">SELECTED</span>
                        </#if>
                        <a href="${url}">${topicsBranch.branch}</a>
                        <ul class="small">
                            <li>
                                Topics:
                                <#list topicsBranch.requests as topicRequest>
                                    <a href="${appUrl.topics().showEditTopicOnBranch(topicRequest.topicName, topicsBranch.branch)}">
                                        ${topicRequest.topicName}</a><#if topicRequest?has_next>,</#if>
                                </#list>
                            </li>
                            <li>Commits: <@branchCommits branchRequests=topicsBranch/></li>
                        </ul>
                    </li>
                </#list>
                <#list clustersBranches as clustersBranch>
                    <#assign url = appUrl.clusters().showClusterResourcesInspectOnBranch(clustersBranch.branch)>
                    <li>
                        <#if clustersBranch.branch == (branch!'')>
                            <span class="badge badge-primary">SELECTED</span>
                        </#if>
                        <a href="${url}">${clustersBranch.branch}</a>
                        <ul class="small">
                            <li>
                                Clusters:
                                <#list clustersBranch.requests as clusterRequest>
                                <a href="${appUrl.clusters().showEditClusterOnBranch(clusterRequest.identifier, clustersBranch.branch)}">
                                    ${clusterRequest.identifier}</a><#if clusterRequest?has_next>,</#if>
                                </#list>
                            </li>
                            <li>Commits: <@branchCommits branchRequests=clustersBranch/></li>
                        </ul>
                    </li>
                </#list>
            </ul>
        </div>
    </#if>

    <hr/>
        <#if branch??>
            <#assign branchDisplay>
                <#if gitBranchBaseUrl??>
                    <#assign url = gitBranchBaseUrl + branch?url>
                    <a target="_blank" href="${url}">${branch}</a>
                <#elseif gitEmbeddedBrowse>
                    <a href="${appUrl.git().showBranch(branch)}">${branch}</a>
                <#else>
                    '${branch}'
                </#if>
            </#assign>
            <h2>Showing resources for branch ${branchDisplay}</h2>
        <#else>
            <h2>Showing current resources</h2>
        </#if>
    <hr/>

    <#assign collapseEnabled = true>
    <#list clustersDiskUsage as clusterIdentifier, optionalClusterRecources>
        <div class="card">
            <div class="card-header">
                <h4>
                    <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
                </h4>
            </div>
            <div class="card-body p-0">
                <#if (optionalClusterRecources.value)??>
                    <#assign clusterResources = optionalClusterRecources.value>
                    <#assign diffModeEnabled = false>
                    <#assign tableTitle = "Current">
                    <#assign collapseId = tableTitle + "-"+ clusterIdentifier?url>
                    <#include "../resourcesInspect.ftl">
                <#else>
                    <div class="alert alert-danger">
                        <span class="badge badge-danger">ERROR</span>
                        ${optionalClusterRecources.absentReason}
                    </div>
                </#if>
                <#if (clustersDiskUsageDiffs)??>
                    <#if (clustersDiskUsageDiffs?api.get(clusterIdentifier))??>
                        <#assign optionalClusterRecourcesDiff = clustersDiskUsageDiffs[clusterIdentifier]>
                        <#if (optionalClusterRecourcesDiff.value)??>
                            <#assign clusterResources = optionalClusterRecourcesDiff.value>
                            <#assign diffModeEnabled = true>
                            <#assign tableTitle = "Diff">
                            <#assign collapseId = tableTitle + "-"+ clusterIdentifier?url>
                            <#include "../resourcesInspect.ftl">
                        <#else>
                            <div class="alert alert-danger">
                                <span class="badge badge-danger">ERROR</span>
                                ${optionalClusterRecourcesDiff.absentReason}
                            </div>
                        </#if>
                    <#else>
                        <div class="alert alert-danger">
                            <span class="badge badge-danger">ERROR</span> Cluster absent in branch
                        </div>
                    </#if>
                </#if>
                <#if (clustersDiskUsageOnBranch)??>
                    <#if (clustersDiskUsageOnBranch?api.get(clusterIdentifier))??>
                        <#assign optionalClusterRecourcesBranch = clustersDiskUsageOnBranch[clusterIdentifier]>
                        <#if (optionalClusterRecourcesBranch.value)??>
                            <#assign clusterResources = optionalClusterRecourcesBranch.value>
                            <#assign diffModeEnabled = false>
                            <#assign tableTitle = "OnBranch">
                            <#assign collapseId = tableTitle + "-"+ clusterIdentifier?url>
                            <#include "../resourcesInspect.ftl">
                        <#else>
                            <div class="alert alert-danger">
                                <span class="badge badge-danger">ERROR</span>
                                ${optionalClusterRecourcesBranch.absentReason}
                            </div>
                        </#if>
                    <#else>
                        <div class="alert alert-danger">
                            <span class="badge badge-danger">ERROR</span> Cluster absent in branch
                        </div>
                    </#if>
                </#if>
            </div>
        </div>
        <br/>
    </#list>
    <#if clustersDiskUsage?size == 0>
        <i>(no clusters to show)</i>
    </#if>
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>
