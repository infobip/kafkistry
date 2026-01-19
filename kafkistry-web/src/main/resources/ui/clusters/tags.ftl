<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="json" type="com.fasterxml.jackson.databind.ObjectMapper" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitBranchBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="pendingClustersRequests"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="pendingBranches"  type="java.util.List<com.infobip.kafkistry.service.history.BranchRequests<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="allTags" type="java.util.List<com.infobip.kafkistry.service.cluster.TagClusters>" -->
<#-- @ftlvariable name="tagTopics" type="java.util.Map<java.lang.String, java.util.List<java.lang.String>>" -->
<#-- @ftlvariable name="clusters" type="java.util.List<com.infobip.kafkistry.model.KafkaCluster>" -->
<#-- @ftlvariable name="enabledClusterIdentifiers"  type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="branchTagClusters"  type="java.util.Map<java.lang.String, java.util.List<java.lang.String>>" -->
<#-- @ftlvariable name="branch"  type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/tags.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterDryRunInspect.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Clusters Tags</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<#macro tagMarker clusterIdentifier tag initTagged tagged>
    <#if tagged && initTagged>
        <#assign btnColorClass = "btn-outline-primary">
    <#elseif tagged && !initTagged>
        <#assign btnColorClass = "btn-outline-success">
    <#elseif !tagged && initTagged>
        <#assign btnColorClass = "btn-outline-danger">
    <#else>
        <#assign btnColorClass = "btn-outline-secondary">
    </#if>
    <label title="Click to tag/untag"
           class="tag-marker-btn btn btn-sm mouse-pointer <#if tagged>active</#if> ${btnColorClass}"
           data-tagged="${tagged?then("yes", "no")}"
           data-cluster="${clusterIdentifier}"
           data-tag="${tag}">
        ${clusterIdentifier}
    </label>
</#macro>

<#macro newTagRow tag="" taggedClusters=[]>
    <tr class="tag-row new-tag" data-tag="">
        <td>
            <div class="row">
                <div class="col pr-1">
                    <input name="new-tag-name" class="form-control" placeholder="Tag name..." title="Tag name" value="${tag}">
                </div>
                <div class="col-auto">
                    <button class="remove-tag-btn btn btn-sm btn-outline-danger">x</button>
                </div>
            </div>
        </td>
        <td>
            <#list clusters as cluster>
                <#assign tagged = taggedClusters?seq_contains(cluster.identifier)>
                <@tagMarker clusterIdentifier=cluster.identifier tag=tag initTagged=false tagged=tagged/>
            </#list>
        </td>
    </tr>
</#macro>


<div class="container">

    <#if gitStorageEnabled>
        <#include "pendingBranches.ftl">
        <br/>
    </#if>

    <#-- hidden clusters data -->
    <div style="display: none;">
        <#list clusters as cluster>
            <div class="cluster-json" data-cluster-json="${json.writeValueAsString(cluster)}"></div>
        </#list>
        <#list enabledClusterIdentifiers as enabledClusterIdentifier>
            <div class="enabled-cluster-identifier" data-cluster-identifier="${enabledClusterIdentifier}"></div>
        </#list>
    </div>


    <div class="card">
        <div class="card-header">
            <#include "../common/backBtn.ftl">
            <span class="h4">
                All tag per clusters
            </span>
            <#if gitStorageEnabled && branch??>
                @
                <#if gitBranchBaseUrl??>
                    <#assign url = gitBranchBaseUrl + branch?url>
                    <a target="_blank" href="${url}">${branch}</a>
                <#elseif gitEmbeddedBrowse>
                    <a href="${appUrl.git().showBranch(branch)}">${branch}</a>
                <#else>
                    branch '${branch}'
                </#if>
            </#if>
        </div>
        <div class="card-body p-0">
            <table class="table table-hover m-0" id="tags-table">
                <tr>
                    <th class="bg-body-secondary">
                        <input type="search" name="tag-filter" class="form-control" title="Simple any-of query to narrow results"
                               placeholder="Tag filter...">
                    </th>
                    <th class="bg-body-secondary">
                        <input type="search" name="cluster-filter" class="form-control" title="Simple any-of query to narrow results"
                               placeholder="Cluster filter...">
                    </th>
                </tr>
                <#if allTags?size == 0>
                    <tr>
                        <td colspan="100">
                            <i>(no tags)</i>
                        </td>
                    </tr>
                </#if>
                <#assign renderedTags = []>
                <#list allTags as tagData>
                    <#assign renderedTags = renderedTags + [tagData.tag]>
                    <tr class="tag-row" data-tag="${tagData.tag}">
                        <td>
                            <span class="badge bg-secondary mb-2">${tagData.tag}</span>
                            <br/>
                            <#if (tagTopics[tagData.tag])??>
                                <#assign topics = tagTopics[tagData.tag]>
                                <i class="small">
                                    <a target="_blank" href="${appUrl.topics().showTopics()}#${tagData.tag}">
                                        ${topics?size} topic(s)
                                    </a>
                                </i>
                            <#else>
                                <i class="small">(no topics)</i>
                            </#if>
                        </td>
                        <td>
                            <#list clusters as cluster>
                                <#assign initTagged = tagData.clusters?seq_contains(cluster.identifier)>
                                <#if branchTagClusters??>
                                    <#assign tagged = (branchTagClusters[tagData.tag]![])?seq_contains(cluster.identifier)>
                                <#else>
                                    <#assign tagged = initTagged>
                                </#if>
                                <@tagMarker clusterIdentifier=cluster.identifier tag=tagData.tag initTagged=initTagged tagged=tagged/>
                            </#list>
                        </td>
                    </tr>
                </#list>
                <#if branchTagClusters??>
                    <#list branchTagClusters as tag, branchTaggedClsters>
                        <#if renderedTags?seq_contains(tag)>
                            <#continue>
                        </#if>
                        <@newTagRow tag=tag taggedClusters=branchTaggedClsters/>
                    </#list>
                </#if>
            </table>
            <button id="add-new-tag-btn" class="btn btn-sm btn-outline-primary m-2">
                Add new tag...
            </button>
        </div>
    </div>
    <table id="tag-template-table" style="display: none;">
        <@newTagRow/>
    </table>
    <br/>

    <div class="card">
        <div class="card-header">
            <h4>Edited tag-clusters</h4>
        </div>
        <div class="card-body">
            <div class="no-edits-status">
                <i>(no edits)</i>
            </div>
            <div class="has-edits-status" style="display: none;">
                <pre class="border m-0" id="tag-edits-diff"></pre>
            </div>
        </div>
    </div>
    <br/>

    <button id="dry-run-all-btn" class="btn btn-sm btn-outline-secondary">
        Dry run inspect all enabled clusters...
    </button>
    <br/>
    <br/>

    <div id="dry-run-all-result" style="display: none;">
        <button class="btn btn-sm btn-secondary mb-1" id="expand-collapse-all-dry-run-btn">Expand/collapse all</button>
        <br/>
        <#list enabledClusterIdentifiers as enabledClusterIdentifier>
            <div class="card">
                <div class="card-header collapsed dry-run-row"
                     data-bs-target=".cluster-dry-run-inspect-result[data-cluster-identifier=${enabledClusterIdentifier}]" data-toggle="collapsing">
                    <div class="row g-2">
                        <div class="col-auto">
                            <span class="h4">
                                <span class="when-collapsed" title="expand...">▼</span>
                                <span class="when-not-collapsed" title="collapse...">△</span>
                                ${enabledClusterIdentifier}
                            </span>
                        </div>
                        <div class="col">
                            <div class="cluster-dry-run-inspect-summary" data-cluster-identifier="${enabledClusterIdentifier}"
                                style="display: none;"></div>
                        </div>
                    </div>
                    <#assign statusId = "cluster-dry-run-inspect-" + enabledClusterIdentifier>
                    <#include "../common/serverOpStatus.ftl">
                    <#assign statusId = "">
                </div>
                <div class="card-body collapseable cluster-dry-run-inspect-result"
                         data-cluster-identifier="${enabledClusterIdentifier}"></div>
            </div>
        </#list>
    </div>

    <hr/>

    <#include "../common/updateForm.ftl">
    <br/>

    <button class="btn btn-sm btn-primary" id="save-clusters-btn">Save changes</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">

    <#include "../common/createPullRequestReminder.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>

