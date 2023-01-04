<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="pendingClustersUpdates"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="clustersStatuses"  type="java.util.List<com.infobip.kafkistry.service.cluster.ClusterStatus>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/clusters.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Clusters</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>

<div class="container">

    <#if gitStorageEnabled>
        <#assign pendingUpdates = pendingClustersUpdates>
        <#assign entityName = "Cluster">
        <#include "../common/pendingChangeRequests.ftl">
        <br/>
    </#if>

    <div class="card">
    <div class="card-header">
        <span class="h4">Status of clusters registry</span>
        <div class="float-right">
            <a href="${appUrl.clusters().showAddCluster()}" class="btn btn-primary mr-2">
                Add new cluster...
            </a>
            <a href="${appUrl.clusters().showTags()}" class="btn btn-secondary mr-2">
                Tags...
            </a>
            <button id="refresh-btn" class="btn btn-outline-secondary">Refresh all</button>
        </div>
    </div>
    <div class="card-body p-0 pt-2 pb-2">

    <#assign disabledCount = 0>
    <table id="clusters" class="table datatable table-bordered m-0">
        <thead class="thead-dark">
        <tr>
            <th>Cluster</th>
            <th>Tags</th>
            <th>Status</th>
            <th>Nodes/Brokers</th>
            <th>Inspect</th>
        </tr>
        </thead>
        <tbody>
        <#list clustersStatuses as clusterStatus>
            <#if clusterStatus.clusterState == "DISABLED">
                <#assign disabledCount++>
                <#continue>
            </#if>
            <#assign cluster = clusterStatus.cluster>
            <#assign clusterIdentifier = cluster.identifier>
            <tr class="cluster-row table-row no-hover"
                data-clusterIdentifier="${clusterIdentifier}"
                data-clusterState="${clusterStatus.clusterState}">
                <td>
                    <a class="btn btn-sm btn-outline-dark" href="${appUrl.clusters().showCluster(clusterIdentifier)}">
                        ${clusterIdentifier}
                    </a>
                </td>
                <td>
                    <#if cluster.tags?size gt 0>
                        <#list cluster.tags as tag>
                            <span class="mb-1 badge badge-secondary">${tag}</span>
                        </#list>
                    <#else>
                        ---
                    </#if>
                </td>
                <td>
                    <@util.namedTypeStatusAlert type=clusterStatus.clusterState/>
                    <br/>
                    <span class="small font-weight-bold">Last refresh:</span><br/>
                    <span class="time small" data-time="${clusterStatus.lastRefreshTime?c}"></span>
                </td>
                <td>
                    <#if clusterStatus.clusterInfo??>
                        <#assign clusterInfo = clusterStatus.clusterInfo>
                        <#include "clusterNodesList.ftl">
                    <#else>
                        ---
                    </#if>
                </td>
                <td>
                    <div id="cluster-brief-inspect-result_${clusterIdentifier}"></div>
                    <#assign statusId = "clusterBriefInspect_"+clusterIdentifier>
                    <#include "../common/serverOpStatus.ftl">
                    <#assign statusId = "">
                </td>
            </tr>
        </#list>
        </tbody>
    </table>

    </div>
    </div>

    <#if disabledCount gt 0>
        <br/>
        <div class="card">
            <div  class="card-header collapsed" data-toggle="collapsing" data-target="#disabled-clusters-card-body">
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
                <span class="h5">Disabled clusters (${disabledCount})</span>
            </div>
            <div id="disabled-clusters-card-body" class="card-body p-0 pt-2 pb-2 collapseable">
                <table id="disabled-clusters" class="table datatable table-bordered m-0">
                    <thead class="thead-dark">
                    <tr>
                        <th>Cluster</th>
                        <th>Tags</th>
                    </tr>
                    </thead>
                    <tbody>
                    <#list clustersStatuses as clusterStatus>
                        <#if clusterStatus.clusterState != "DISABLED">
                            <#continue>
                        </#if>
                        <#assign cluster = clusterStatus.cluster>
                        <#assign clusterIdentifier = cluster.identifier>
                        <tr class="cluster-row table-row no-hover"
                            data-clusterIdentifier="${clusterIdentifier}"
                            data-clusterState="${clusterStatus.clusterState}">
                            <td>
                                <a class="btn btn-sm btn-outline-dark" href="${appUrl.clusters().showCluster(clusterIdentifier)}">
                                    ${clusterIdentifier}
                                </a>
                            </td>
                            <td>
                                <#if cluster.tags?size gt 0>
                                    <#list cluster.tags as tag>
                                        <span class="mb-1 badge badge-secondary">${tag}</span>
                                    </#list>
                                <#else>
                                    ---
                                </#if>
                            </td>
                        </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
        </div>
    </#if>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
