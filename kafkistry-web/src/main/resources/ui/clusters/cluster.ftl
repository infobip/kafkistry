<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterStatus"  type="com.infobip.kafkistry.service.cluster.ClusterStatus" -->
<#-- @ftlvariable name="pendingClusterRequests"  type="java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="brokerConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->

<html lang="en">

<#assign clusterModel = clusterStatus.cluster>
<#assign clusterIdentifier = clusterModel.identifier>

<head>
    <#include "../commonResources.ftl"/>
    <meta name="cluster-identifier" content="${clusterIdentifier}">
    <script src="static/cluster/cluster.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>

<div class="container">
    <h3>Cluster: <span class="text-monospace">${clusterIdentifier}</span></h3>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-secondary dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Registry action...
        </button>
        <div class="dropdown-menu open">
            <button id="refresh-btn" class="dropdown-item text-secondary">
                Refresh
            </button>
            <a href="${appUrl.clusters().showEditCluster(clusterIdentifier)}" class="dropdown-item text-primary">
                Edit cluster metadata...
            </a>
            <a href="${appUrl.clusters().showRemoveCluster(clusterIdentifier)}" class="dropdown-item text-danger">
                Remove from registry...
            </a>
        </div>
    </div>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Create all missing...
        </button>
        <div class="dropdown-menu open">
            <a href="${appUrl.topicsManagement().showBulkCreateMissingTopics(clusterIdentifier)}"
               class="dropdown-item text-primary">
                Create missing topics...
            </a>
            <a href="${appUrl.acls().showBulkCreateClusterRules(clusterIdentifier)}"
               class="dropdown-item text-primary">
                Create missing ACLs...
            </a>
            <a href="${appUrl.quotas().showBulkCreateClusterQuotas(clusterIdentifier)}"
               class="dropdown-item text-primary">
                Create missing quotas...
            </a>
        </div>
    </div>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-info dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Topic<small>(s)</small> action...
        </button>
        <div class="dropdown-menu open">
            <a href="${appUrl.topicsManagement().showBulkConfigUpdates(clusterIdentifier)}"
               class="dropdown-item text-info">
                Update topics wrong configs...
            </a>
            <a href="${appUrl.topicsManagement().showBulkReElectReplicaLeaders(clusterIdentifier)}"
               class="dropdown-item text-info">
                Re-elect topics leaders...
            </a>
            <a href="${appUrl.topicsManagement().showBulkVerifyReAssignments(clusterIdentifier)}"
               class="dropdown-item text-info">
                Verify assignments/remove throttle...
            </a>
            <a href="${appUrl.topicsManagement().showBulkReBalanceTopicsForm(clusterIdentifier)}"
               class="dropdown-item text-info">
                Bulk re-assign/re-balance topics...
            </a>
            <a href="${appUrl.topicsManagement().showBulkDeleteUnwantedTopicsOnCluster(clusterIdentifier)}"
               class="dropdown-item text-info">
                Bulk delete unwanted topics...
            </a>

        </div>
    </div>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-dark dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Throttle...
        </button>
        <div class="dropdown-menu open">
            <a href="${appUrl.clustersManagement().showApplyThrottling(clusterIdentifier)}"
               class="dropdown-item text-dark">
                Change throttle rate...
            </a>
            <a href="${appUrl.topicsManagement().showThrottleBrokerPartitionsForm(clusterIdentifier)}"
               class="dropdown-item text-dark">
                Throttle specific broker(s)/topic(s)...
            </a>
        </div>
    </div>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-light dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Inspect...
        </button>
        <div class="dropdown-menu open">
            <a href="${appUrl.clusters().showClusterBalance(clusterIdentifier)}" class="dropdown-item text-secondary">
                Cluster balance...
            </a>
            <a href="${appUrl.clusters().showClusterResources(clusterIdentifier)}" class="dropdown-item text-secondary">
                Resources...
            </a>
        </div>
    </div>

    <br/>

    <table class="table table-sm fixed-layout mt-3">
        <tr>
            <th>Cluster identifier</th>
            <td>${clusterIdentifier}</td>
        </tr>
        <tr>
            <th>Tags</th>
            <td>
                <#if clusterModel.tags?size == 0>
                    <i>(no tags)</i>
                <#else>
                    <#list clusterModel.tags as tag>
                        <span class="mb-1 badge badge-secondary">${tag}</span>
                    </#list>
                </#if>
            </td>
        </tr>
        <tr>
            <th>Connection protocol</th>
            <td>
                <#if clusterModel.sslEnabled && clusterModel.saslEnabled>SASL_SSL (authentication + encrypted connection)
                <#elseif clusterModel.sslEnabled && !clusterModel.saslEnabled>SSL (no authentication + encrypted connection)
                <#elseif !clusterModel.sslEnabled && clusterModel.saslEnabled>SASL_PLAINTEXT (authentication + plain connection)
                <#else>PLAINTEXT (no authentication + plain connection)</#if>
            </td>
        </tr>
        <tr>
            <th>Properties profiles</th>
            <td>
                <#if clusterModel.profiles?size == 0>
                    ----
                </#if>
                <#list clusterModel.profiles as profile>
                    <span class="badge badge-light">${profile}</span>
                </#list>
            </td>
        </tr>
        <tr>
            <th>Cluster state</th>
            <td><@util.namedTypeStatusAlert type=clusterStatus.clusterState/></td>
        </tr>
        <tr>
            <th>Last refresh</th>
            <td class="time" data-time="${clusterStatus.lastRefreshTime?c}"></td>
        </tr>
        <#if gitStorageEnabled>
            <tr>
                <th>Pending changes</th>
                <td>
                    <#assign pendingRequests = pendingClusterRequests>
                    <#include "../common/pendingChanges.ftl" >
                </td>
            </tr>
        </#if>
    </table>
    <br/>

    <div class="card">
        <div class="card-header">
            <span class="h4">Cluster issues</span>
        </div>
        <div class="card-body">
            <#assign statusId = "clusterIssues">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="cluster-issues-result"></div>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#topics-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Status per topic on this cluster</span>
        </div>
        <div id="topics-card-body" class="card-body collapseable p-0 pb-2">
            <#assign statusId = "clusterTopics">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="cluster-topics-result"></div>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#acls-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Status per ACLs on this cluster</span>
        </div>
        <div id="acls-card-body" class="card-body collapseable p-0 pb-2">
            <#assign statusId = "clusterAcls">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="cluster-acls-result"></div>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#quotas-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Status per entity quotas on this cluster</span>
        </div>
        <div id="quotas-card-body" class="card-body collapseable p-0 pb-2">
            <#assign statusId = "clusterQuotas">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="cluster-quotas-result"></div>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#consumer-groups-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Status per consumer group on this cluster</span>
        </div>
        <div id="consumer-groups-card-body" class="card-body collapseable p-0 pb-2">
            <#assign statusId = "clusterConsumerGroups">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="cluster-consumer-groups-result"></div>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#cluster-metadata-info-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Cluster metadata info / broker config properties</span>
        </div>

        <div id="cluster-metadata-info-card-body" class="card-body collapseable p-0">
            <#if clusterStatus.clusterInfo??>
                <#assign clusterInfo = clusterStatus.clusterInfo>
                <table class="table table-sm">
                    <tr>
                        <th>Cluster id</th>
                        <td>${clusterInfo.clusterId}</td>
                    </tr>
                    <tr>
                        <th>Identifier</th>
                        <td>${clusterInfo.identifier}</td>
                    </tr>
                    <tr>
                        <th>Controller node id</th>
                        <td>${clusterInfo.controllerId}</td>
                    </tr>
                    <tr>
                        <th>Nodes/Broker ids</th>
                        <td><#include "clusterNodesList.ftl"></td>
                    </tr>
                    <tr>
                        <th>Connection</th>
                        <td>${clusterInfo.connectionString}</td>
                    </tr>
                    <tr>
                        <th>Broker config</th>
                        <td>
                            <ul class="nav">
                                <#list clusterInfo.nodeIds as brokerId>
                                    <li>
                                        <#assign active = (brokerId == clusterInfo.controllerId)?then("active", "")>
                                        <a class="btn btn-sm btn-outline-dark m-1 ${active}" data-toggle="tab"
                                           href="#broker-${brokerId?c}-config">
                                            ${brokerId?c}
                                        </a>
                                    </li>
                                </#list>
                            </ul>
                        </td>
                    </tr>
                </table>
                <div class="tab-content" style="max-height: 700px; overflow-y: scroll;">
                    <#list clusterInfo.nodeIds as brokerId>
                        <#assign active = (brokerId == clusterInfo.controllerId)?then("active", "")>
                        <div id="broker-${brokerId?c}-config" class="tab-pane ${active}">
                            <#if clusterInfo.perBrokerConfig?api.containsKey(brokerId)>
                                <#assign config = clusterInfo.perBrokerConfig?api.get(brokerId)>
                                <#include "../common/existingConfig.ftl">
                            <#else>
                                ---
                            </#if>
                        </div>
                    </#list>
                </div>


            <#else>
                <p><i>(nothing to show because cluster state is ${clusterStatus.clusterState})</i></p>
            </#if>
        </div>

    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
