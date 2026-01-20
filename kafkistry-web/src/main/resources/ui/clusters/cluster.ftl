<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterStatus"  type="com.infobip.kafkistry.service.cluster.ClusterStatus" -->
<#-- @ftlvariable name="pendingClusterRequests"  type="java.util.List<com.infobip.kafkistry.service.history.ClusterRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="brokerConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->
<#-- @ftlvariable name="autopilotEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="autopilotActions"  type="java.util.List<com.infobip.kafkistry.autopilot.repository.ActionFlow>" -->

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
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h3>Cluster: <span class="font-monospace">${clusterIdentifier}</span></h3>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-secondary dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Registry action...
        </button>
        <div class="dropdown-menu">
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
        <button type="button" class="btn btn-primary dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Create all missing...
        </button>
        <div class="dropdown-menu">
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
        <button type="button" class="btn btn-info dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Topic<small>(s)</small> action...
        </button>
        <div class="dropdown-menu">
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
        <button type="button" class="btn btn-dark dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Throttle...
        </button>
        <div class="dropdown-menu">
            <a href="${appUrl.clustersManagement().showApplyThrottling(clusterIdentifier)}"
               class="dropdown-item text-secondary">
                Change throttle rate...
            </a>
            <a href="${appUrl.topicsManagement().showThrottleBrokerPartitionsForm(clusterIdentifier)}"
               class="dropdown-item text-secondary">
                Throttle specific broker(s)/topic(s)...
            </a>
        </div>
    </div>

    <div class="dropdown btn-group">
        <button type="button" class="btn btn-light dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
            Inspect...
        </button>
        <div class="dropdown-menu">
            <a href="${appUrl.clusters().showClusterBalance(clusterIdentifier)}" class="dropdown-item text-secondary">
                Cluster balance...
            </a>
            <a href="${appUrl.clusters().showClusterResources(clusterIdentifier)}" class="dropdown-item text-secondary">
                Resources...
            </a>
        </div>
    </div>

    <br/>

    <table class="table table-hover table-sm mt-3">
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
                        <span class="mb-1 badge bg-secondary">${tag}</span>
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
                    <span class="badge bg-light">${profile}</span>
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
        <#if clusterStatus.clusterInfo??>
            <#assign clusterInfo = clusterStatus.clusterInfo>
            <tr>
                <th>Cluster id</th>
                <td><code>${clusterInfo.clusterId}</code></td>
            </tr>
            <tr>
                <th>Cluster version</th>
                <td>
                    <#if clusterInfo.clusterVersion??>
                        <code>${clusterInfo.clusterVersion.toString()}</code>
                    <#else>
                        <i>(n/a)</i>
                    </#if>
                </td>
            </tr>
            <tr>
                <th>Consensus type</th>
                <td>
                    <#assign clusterConsensusType = clusterInfo.kraftEnabled?then("KRaft", "Zookeper")>
                    <span class="badge bg-neutral">${clusterConsensusType}</span>
                </td>
            </tr>
            <#if clusterInfo.kraftEnabled>
                <tr>
                    <th>Leader</th>
                    <td>
                        <strong>ID</strong>:${clusterInfo.quorumInfo.leaderId?c}
                        <strong>Epoch</strong>:${clusterInfo.quorumInfo.leaderEpoch?c}
                        <strong>High-Watermark</strong>:${clusterInfo.quorumInfo.highWatermark?c}
                    </td>
                </tr>
                <#macro quorumReplicas replicas>
                <#-- @ftlvariable name="replicas" type="java.util.List<com.infobip.kafkistry.kafka.QuorumReplicaState>" -->
                    <table>
                        <tr>
                            <th>Replica ID</th>
                            <th>Log end offset</th>
                            <th>Last fetch</th>
                            <th>Last caught up</th>
                        </tr>
                        <#list replicas as replica>
                            <tr>
                                <td>${replica.replicaId?c}</td>
                                <td>${replica.logEndOffset?c}</td>
                                <td class="small">
                                    <#if replica.lastFetchTimestamp??>
                                        <span class="time" data-time="${replica.lastFetchTimestamp?c}"></span>
                                    <#else>
                                        <i>n/a</i>
                                    </#if>
                                </td>
                                <td class="small">
                                    <#if replica.lastCaughtUpTimestamp??>
                                        <span class="time" data-time="${replica.lastCaughtUpTimestamp?c}"></span>
                                    <#else>
                                        <i>n/a</i>
                                    </#if>
                                </td>
                            </tr>
                        </#list>
                    </table>
                </#macro>
                <tr>
                    <th>Voters</th>
                    <td>
                        <#if clusterInfo.quorumInfo.voters?size == 0>
                            <i>(none)</i>
                        <#else>
                            <@quorumReplicas replicas=clusterInfo.quorumInfo.voters/>
                        </#if>
                    </td>
                </tr>
                <tr>
                    <th>Observers</th>
                    <td>
                        <#if clusterInfo.quorumInfo.observers?size == 0>
                            <i>(none)</i>
                        <#else>
                            <@quorumReplicas replicas=clusterInfo.quorumInfo.observers/>
                        </#if>
                    </td>
                </tr>
            <#else>
                <tr>
                    <th>Controller node id</th>
                    <td>${clusterInfo.controllerId}</td>
                </tr>
            </#if>
            <tr>
                <th>Nodes/Broker ids</th>
                <td><#include "clusterNodesList.ftl"></td>
            </tr>
            <tr>
                <th>Connection</th>
                <td class="small">${clusterInfo.connectionString}</td>
            </tr>
        </#if>
        <#if autopilotEnabled>
            <tr class="<#if autopilotActions?size gt 0>no-hover</#if>">
                <th>Autopilot</th>
                <td>
                    <#assign actionsSearchTerm = clusterIdentifier>
                    <#include "../autopilot/relatedActions.ftl">
                </td>
            </tr>
        </#if>
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
        <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#topics-card-body">
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
        <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#acls-card-body">
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
        <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#quotas-card-body">
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
        <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#consumer-groups-card-body">
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
        <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#cluster-metadata-info-card-body">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="h4">Cluster metadata info / broker config properties</span>
        </div>

        <div id="cluster-metadata-info-card-body" class="card-body collapseable p-0">
            <#if clusterStatus.clusterInfo??>
                <#assign clusterInfo = clusterStatus.clusterInfo>
                <div>
                    <table class="table table-hover table-sm">
                        <tr class="table-theme-dark"><th colspan="100">Cluster features</th></tr>
                        <tr>
                            <th>Finalized Features Epoch</th>
                            <td>${clusterInfo.features.finalizedFeaturesEpoch}</td>
                        </tr>
                        <tr class="table-theme-dark"><th>Finalized Feature</th><th>Versions range (min..max)</th></tr>
                        <#list clusterInfo.features.finalizedFeatures as feature, versions>
                            <tr>
                                <th>${feature}</th>
                                <td>
                                    [<code>${versions.minVersion}</code>..<code>${versions.maxVersion}</code>]
                                </td>
                            </tr>
                        </#list>
                        <#if clusterInfo.features.finalizedFeatures?size == 0>
                            <tr><td colspan="100" class="text-center"><i>(empty)</i></td></tr>
                        </#if>
                        <tr class="table-theme-dark"><th>Supported Feature</th><th>Versions range  (min..max)</th></tr>
                        <#list clusterInfo.features.supportedFeatures as feature, versions>
                            <tr>
                                <th>${feature}</th>
                                <td>
                                    [<code>${versions.minVersion}</code>..<code>${versions.maxVersion}</code>]
                                </td>
                            </tr>
                        </#list>
                        <#if clusterInfo.features.supportedFeatures?size == 0>
                            <tr><td colspan="100" class="text-center"><i>(empty)</i></td></tr>
                        </#if>
                    </table>
                </div>
                <table class="table table-hover table-sm">
                    <tr>
                        <th>Select node config / Api keys</th>
                        <td>
                            <ul class="nav">
                                <#list clusterInfo.nodeIds as nodeId>
                                    <li>
                                        <#assign active = (nodeId == clusterInfo.controllerId)?then("active", "")>
                                        <a class="btn btn-sm btn-outline-secondary m-1 ${active}" data-bs-toggle="tab"
                                           href="#node-${nodeId?c}-config">
                                            ${nodeId?c}
                                        </a>
                                    </li>
                                </#list>
                            </ul>
                        </td>
                    </tr>
                </table>
                <div class="tab-content" style="max-height: 700px; overflow-y: scroll;">
                    <#list clusterInfo.nodeIds as nodeId>
                        <#assign active = (nodeId == clusterInfo.controllerId)?then("active", "")>
                        <div id="node-${nodeId?c}-config" class="tab-pane ${active}">
                            <#if clusterInfo.apiKeys?api.containsKey(nodeId)>
                                <#assign nodeApiKeys = clusterInfo.apiKeys?api.get(nodeId)>
                                <p>
                                    ZK migration:
                                    <#if nodeApiKeys.zkMigrationEnabled>
                                        <span class="badge bg-success">ENABLED</span>
                                    <#else>
                                        <span class="badge bg-secondary">NOT ENABLED</span>
                                    </#if>
                                </p>
                                <table class="table table-hover table-sm">
                                    <thead class="table-theme-dark">
                                    <tr>
                                        <th>Api Id</th>
                                        <th>Api name</th>
                                        <th>Cluster action</th>
                                        <th>Forwardable</th>
                                        <th>Delayed allocation</th>
                                        <th>Min broker magic</th>
                                        <th>Min version</th>
                                        <th>Max version</th>
                                        <th>Latest usable version / unusable reason</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <#list nodeApiKeys.apiKeys as apiKey>
                                        <tr>
                                            <td><code>${apiKey.id}</code></td>
                                            <#if apiKey.metadata??>
                                                <td><span class="badge bg-neutral">${apiKey.metadata.name}</span></td>
                                                <td>
                                                    <#if apiKey.metadata.clusterAction>
                                                        <span class="badge bg-primary">YES</span>
                                                    <#else>
                                                        <span class="badge bg-secondary">NO</span>
                                                    </#if>
                                                </td>
                                                <td>
                                                    <#if apiKey.metadata.forwardable>
                                                        <span class="badge bg-primary">YES</span>
                                                    <#else>
                                                        <span class="badge bg-secondary">NO</span>
                                                    </#if>
                                                </td>
                                                <td>
                                                    <#if apiKey.metadata.requiresDelayedAllocation>
                                                        <span class="badge bg-primary">REQUIRED</span>
                                                    <#else>
                                                        <span class="badge bg-secondary">NOT REQUIRED</span>
                                                    </#if>
                                                </td>
                                                <td><code>${apiKey.metadata.minRequiredInterBrokerMagic}</code></td>
                                            <#else>
                                                <td><span class="badge bg-warning">UNKNOWN</span></td>
                                                <td colspan="4">---</td>
                                            </#if>
                                            <td>${apiKey.minVersion!'---'}</td>
                                            <td>${apiKey.maxVersion!'---'}</td>
                                            <td>
                                                <#if apiKey.latestUsableVersion??>
                                                    ${apiKey.latestUsableVersion}
                                                <#elseif apiKey.unusableReason??>
                                                    <span class="text-danger">${apiKey.unusableReason}</span>
                                                <#else>
                                                    ---
                                                </#if>
                                            </td>
                                        </tr>
                                    </#list>
                                    </tbody>
                                </table>
                            <#else>
                                ---
                            </#if>
                            <#if clusterInfo.perBrokerConfig?api.containsKey(nodeId)>
                                <#assign config = clusterInfo.perBrokerConfig?api.get(nodeId)>
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
