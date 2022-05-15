<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->
<#-- @ftlvariable name="clusterState"  type="com.infobip.kafkistry.kafkastate.StateData<com.infobip.kafkistry.kafkastate.KafkaClusterState>" -->
<#-- @ftlvariable name="pendingClusterRequests"  type="java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>" -->
<#-- @ftlvariable name="pendingTopicsRequests"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.TopicRequest>>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="brokerConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->

<html lang="en">

<#assign clusterModel = clusterTopics.cluster>
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
            <a href="${appUrl.topicsManagement().showBulkReElectReplicaLeaders(clusterIdentifier)}"
               class="dropdown-item text-info">
                Re-elect topics leaders...
            </a>
            <a href="${appUrl.topicsManagement().showBulkVerifyReAssignments(clusterIdentifier)}"
               class="dropdown-item text-info">
                Verify re-assignments...
            </a>
            <a href="${appUrl.topicsManagement().showBulkReBalanceTopicsForm(clusterIdentifier)}"
               class="dropdown-item text-info">
                Bulk re-balance topics...
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
                Throttle specific broker(s)...
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
            <th>Properies profiles</th>
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
            <td>
                <#assign stateClass = util.clusterStatusToHtmlClass(clusterTopics.clusterState)>
                <div class="alert ${stateClass} mb-0" role="alert">
                    ${clusterTopics.clusterState.name()}
                </div>
            </td>
        </tr>
        <tr>
            <th>Last refresh</th>
            <td class="time" data-time="${clusterTopics.lastRefreshTime?c}"></td>
        </tr>
        <tr>
            <th>Aggregated topics status counts</th>
            <td>
                <#if clusterTopics.topicsStatusCounts??>
                    <table class="table table-sm mb-0">
                        <#list clusterTopics.topicsStatusCounts as statusType, count>
                            <tr>
                                <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType}"
                                    title="Click to filter by...">
                                    <#include "../common/topicStatusResultBox.ftl">
                                </td>
                                <td style="text-align: right;">${count}</td>
                            </tr>
                        </#list>
                    </table>
                <#else>
                    <i>(no data)</i>
                </#if>
            </td>
        </tr>
        <tr>
            <th>Issues</th>
            <td>
                <#assign statusId = "clusterIssues">
                <#include "../common/serverOpStatus.ftl">
                <#assign statusId = "">
                <div id="cluster-issues-result"></div>
            </td>
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
            <span class="h4">Status per topic on this cluster</span>
        </div>

        <div class="card-body pl-0 pr-0">
            <#include "../common/loading.ftl">
            <table id="topics" class="table table-bordered datatable" style="display: none;">
                <thead class="thead-dark">
                <tr>
                    <th>Topic name</th>
                    <th style="width: 200px;">Topic status</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <#if clusterTopics.statusPerTopics??>
                    <#list clusterTopics.statusPerTopics as topicStatus>
                        <#assign topicName = topicStatus.topicName>
                        <#assign statusTypes = util.enumListToStringList(topicStatus.status.types)>
                        <#assign presentInRegistry = !statusTypes?seq_contains("UNKNOWN")>
                        <tr class="topic-row table-row">
                            <td>
                                <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)}">
                                    <button class="btn btn-sm btn-outline-dark"
                                            title="Inspect this topic on this cluster...">
                                        <#if presentInRegistry>
                                            <span class="font-weight-bold">${topicStatus.topicName} 🔍</span>
                                        <#else>
                                            <span class="text-info">${topicStatus.topicName} 🔍</span>
                                        </#if>
                                    </button>
                                </a>
                            </td>
                            <td style="width: 200px;">
                                <#assign topicOnClusterStatus = topicStatus.status>
                                <#include "../common/topicOnClusterStatus.ftl">
                            </td>
                            <td>
                                <#assign availableActions = topicStatus.status.availableActions>
                                <#include "../common/topicOnClusterAction.ftl">
                            </td>
                        </tr>
                    </#list>
                </#if>
                </tbody>
            </table>
        </div>
    </div>

    <br/>
    <br/>
    <div class="card">
        <div class="card-header">
            <span class="h4">Actual cluster latest metadata info</span>
        </div>

        <div class="card-body p-0">
            <#if clusterState.valueOrNull()??>
                <#assign clusterInfo = clusterState.value().clusterInfo>
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
                <p><i>(nothing to show because cluster state is ${clusterTopics.clusterState.toString()})</i></p>
            </#if>
        </div>

    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
