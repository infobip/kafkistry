<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clustersTopics"  type="java.util.List<com.infobip.kafkistry.service.topic.ClusterTopicsStatuses>" -->
<#-- @ftlvariable name="pendingClustersUpdates"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

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
            <button id="refresh-btn" class="btn btn-outline-secondary">Refresh all</button>
        </div>
    </div>
    <div class="card-body p-0 pt-2 pb-2">

    <table id="clusters" class="table datatable table-bordered m-0">
        <thead class="thead-dark">
        <tr>
            <th>#</th>
            <th>Cluster/tags</th>
            <th>Status</th>
            <th>Nodes/Brokers</th>
            <th>Topics status / # topics</th>
        </tr>
        </thead>
        <tbody>
        <#list clustersTopics as clusterInspection>
            <#assign clusterIdentifier = clusterInspection.cluster.identifier>
            <tr class="cluster-row table-row no-hover">
            <td>${clusterInspection?index + 1}</td>
            <td>
                <a class="btn btn-sm btn-outline-dark" href="${appUrl.clusters().showCluster(clusterIdentifier)}">
                    ${clusterIdentifier}
                </a>
                <#if clusterInspection.cluster.tags?size gt 0>
                    <hr/>
                    <#list clusterInspection.cluster.tags as tag>
                        <span class="mb-1 badge badge-secondary">${tag}</span>
                    </#list>
                </#if>
            </td>
            <td>
                <#assign stateClass = util.clusterStatusToHtmlClass(clusterInspection.clusterState)>
                <div class="alert ${stateClass} mb-0" role="alert">
                    ${clusterInspection.clusterState.name()}
                </div>
                <br/>
                <span class="small font-weight-bold">Last refresh:</span><br/>
                <span class="time small" data-time="${clusterInspection.lastRefreshTime?c}"></span>
            </td>
            <td>
                <#if clusterInspection.clusterInfo??>
                    <#assign clusterInfo = clusterInspection.clusterInfo>
                    <#include "clusterNodesList.ftl">
                <#else>
                    ---
                </#if>
            </td>
            <#if clusterInspection.topicsStatusCounts??>
                <td class="p-0">
                    <table class="m-0 table">
                        <#list clusterInspection.topicsStatusCounts as statusType, count>
                            <tr>
                                <td class="agg-count-status-type">
                                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                                       href="${appUrl.clusters().showCluster(clusterIdentifier)}#topics-table|${statusType}">
                                        <#include "../common/topicStatusResultBox.ftl">
                                    </a>
                                </td>
                                <td class="col-1" style="text-align: right;">${count}</td>
                            </tr>
                        </#list>
                    </table>
                </td>
            <#else>
                <td><i>(no data)</i></td>
            </#if>
            </tr>
        </#list>
        </tbody>
    </table>

    </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
