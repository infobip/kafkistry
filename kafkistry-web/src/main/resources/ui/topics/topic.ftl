<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topic"  type="com.infobip.kafkistry.service.topic.TopicStatuses" -->
<#-- @ftlvariable name="topicOwned"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="pendingTopicRequests"  type="java.util.List<com.infobip.kafkistry.service.history.PendingRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="autopilotEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="autopilotActions"  type="java.util.List<com.infobip.kafkistry.autopilot.repository.ActionFlow>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/topic.js?ver=${lastCommit}"></script>
    <script src="static/dateTimeFormatter.js?ver=${lastCommit}"></script>
    <script src="static/git/entityHistory.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<#assign topicName = topic.topicName>
<#assign existInRegistry = topic.topicDescription??>

<div class="container">
    <h3>Topic: <span class="text-monospace">${topicName}</span></h3>

    <table class="table table-sm">
        <tr>
            <th>Name</th>
            <td>${topicName}</td>
        </tr>
        <tr>
            <th>Topic in registry</th>
            <td><@util.inRegistry flag = existInRegistry/></td>
        </tr>

        <tr>
            <th>Owner</th>
            <td>
                <#if existInRegistry>
                    ${topic.topicDescription.owner}
                    <#if topicOwned>
                        <@util.yourOwned what="topic"/>
                    </#if>
                <#else>
                    <span class="text-primary text-monospace small">[none]</span>
                </#if>
            </td>
        </tr>
        <#if existInRegistry>
            <tr>
                <th>Description</th>
                <td style="white-space: pre-wrap;" id="description" class="text-links">${topic.topicDescription.description}</td>
            </tr>
            <tr>
                <th>Labels</th>
                <td>
                    <#if topic.topicDescription.labels?size == 0>
                        ---
                    <#else>
                        <#list topic.topicDescription.labels as label>
                            <span class="badge badge-secondary" title="Label category">${label.category}</span>
                            <span class="text-monospace" title="Label name">${label.name}</span>
                            &nbsp;
                        </#list>
                    </#if>
                </td>
            </tr>
            <tr>
                <th>Producer</th>
                <td>${topic.topicDescription.producer}</td>
            </tr>
            <tr>
                <th>Presence</th>
                <td>
                    <@util.presence presence = topic.topicDescription.presence/>
                </td>
            </tr>
            <tr>
                <th>Frozen properties</th>
                <td>
                    <#if topic.topicDescription.freezeDirectives?size == 0>
                        ---
                    <#else>
                        <ul>
                            <#assign padlockSymol>&#128274;</#assign>
                            <#list topic.topicDescription.freezeDirectives as freezeDirectve>
                                <li>
                                    <strong>Reason</strong>: <span class="text-links">${freezeDirectve.reasonMessage}</span>
                                    <ul>
                                        <#if freezeDirectve.partitionCount>
                                            <li><span class="badge badge-secondary">Partition count</span> ${padlockSymol}</li>
                                        </#if>
                                        <#if freezeDirectve.replicationFactor>
                                            <li><span class="badge badge-secondary">Replication factor</span> ${padlockSymol}</li>
                                        </#if>
                                        <#list freezeDirectve.configProperties as frozenProperty>
                                            <li><code>${frozenProperty}</code> ${padlockSymol}</li>
                                        </#list>
                                    </ul>
                                </li>
                            </#list>
                        </ul>
                    </#if>
                </td>
            </tr>
        </#if>

        <tr>
            <th>Status over <br/>all clusters</th>
            <td>
                <#assign statusFlags = topic.aggStatusFlags>
                <#assign clusterStatusFlags = util.clusterStatusFlags(topic.statusPerClusters)>
                <#include "../common/statusFlags.ftl">
            </td>
        </tr>
        <#if gitStorageEnabled>
            <tr>
                <th>Pending changes</th>
                <td>
                    <#assign pendingRequests = (pendingTopicRequests![]) >
                    <#include "../common/pendingChanges.ftl" >
                </td>
            </tr>
        </#if>
        <#if autopilotEnabled>
            <tr class="<#if autopilotActions?size gt 0>no-hover</#if>">
                <th>Autopilot</th>
                <td>
                    <#assign actionsSearchTerm = topicName>
                    <#include "../autopilot/relatedActions.ftl">
                </td>
            </tr>
        </#if>
        <tr>
            <th>Actions</th>
            <td>
                <#assign topicActions = util.enumListToStringList(topic.availableActions)>
                <#if existInRegistry>
                    <p class="float-left mr-2">
                        <a href="${appUrl.topics().showDeleteTopic(topicName)}">
                            <button class="btn btn-outline-danger btn-sm">Delete topic from registry...</button>
                        </a>
                    </p>

                    <p class="float-left mr-2">
                        <a href="${appUrl.topics().showEditTopic(topicName)}">
                            <button class="btn btn-outline-primary btn-sm">Edit topic...</button>
                        </a>
                    </p>

                    <p class="float-left mr-2">
                        <a href="${appUrl.topics().showCloneAddNewTopic(topicName)}">
                            <button class="btn btn-outline-primary btn-sm">Clone as new...</button>
                        </a>
                    </p>

                    <p class="float-left mr-2">
                        <a href="${appUrl.compare().showComparePage(topicName)}">
                            <button class="btn btn-outline-info btn-sm">Compare...</button>
                        </a>
                    </p>

                    <#if topicActions?seq_contains("CREATE_TOPIC")>
                        <p class="float-left mr-2">
                            <a href="${appUrl.topicsManagement().showBulkCreateMissingTopicOnClusters(topicName)}">
                                <button class="btn btn-outline-primary btn-sm">Create where missing...</button>
                            </a>
                        </p>
                    </#if>

                    <#if topicActions?seq_contains("ALTER_TOPIC_CONFIG")>
                        <p class="float-left mr-2">
                            <a href="${appUrl.topicsManagement().showTopicConfigBulkUpdate(topicName)}">
                                <button class="btn btn-outline-warning btn-sm">Alter config where wrong...</button>
                            </a>
                        </p>
                    </#if>

                    <p class="float-left mr-2">
                        <a href="${appUrl.recordsStructure().showTopicStructurePage(topicName)}">
                            <button class="btn btn-outline-info btn-sm">Records structure...</button>
                        </a>
                    </p>
                <#else>
                    <#if topicActions?seq_contains("IMPORT_TOPIC")>
                        <p class="float-left mr-2">
                            <a href="${appUrl.topics().showImportTopic(topicName)}">
                                <button class="btn btn-outline-primary btn-sm">
                                    Import topic... <@info.icon tooltip=doc.importTopicBtn/>
                                </button>
                            </a>
                        </p>
                    </#if>
                </#if>

                <#if topicActions?seq_contains("DELETE_TOPIC_ON_KAFKA")>
                    <p class="float-left mr-2">
                        <a href="${appUrl.topicsManagement().showBulkDeleteUnwantedTopicOnClusters(topicName)}">
                            <button class="btn btn-outline-danger btn-sm">Delete where unwanted...</button>
                        </a>
                    </p>
                </#if>
            </td>
        </tr>
    </table>

    <div class="card">
    <div class="card-header">
        <h4>Statuses per clusters</h4>
    </div>
    <div class="card-body p-0">
    <table class="table">
        <thead class="thead-dark table-lg">
        <tr>
            <th>Cluster</th>
            <th>Status</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody class="table-sm">
        <#if topic.statusPerClusters?size == 0>
            <tr>
                <td colspan="100">
                    <i>(no clusters to show)</i>
                </td>
            </tr>
        </#if>
        <#list topic.statusPerClusters as clusterStatus>
            <tr class="per-cluster-status-row">
                <td>
                    <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterStatus.clusterIdentifier)}">
                        <button class="btn btn-sm btn-outline-dark text-nowrap" title="Inspect this topic on this cluster...">
                            ${clusterStatus.clusterIdentifier} 🔍
                        </button>
                    </a>
                </td>
                <td>
                    <#assign topicOnClusterStatus = clusterStatus.status>
                    <#include "../common/topicOnClusterStatus.ftl">
                </td>
                <td>
                    <#assign clusterIdentifier = clusterStatus.clusterIdentifier>
                    <#assign availableActions = clusterStatus.status.availableActions>
                    <#include "../common/topicOnClusterAction.ftl">
                </td>
            </tr>
        </#list>
        </tbody>
    </table>
    </div>
    </div>


    <#if existInRegistry>
        <br/>
        <div class="card">
            <div class="card-header">
                <h4>Field descriptions</h4>
            </div>
            <div class="card-body p-0">
                <table class="table table-sm m-0">
                    <thead class="thead-dark">
                    <tr>
                        <th>Field name/selector</th>
                        <th>Classifications</th>
                        <th>Description</th>
                    </tr>
                    </thead>
                    <#if topic.topicDescription.fieldDescriptions?size != 0>
                        <#list topic.topicDescription.fieldDescriptions as fieldDescription>
                            <tr>
                                <td><code>${fieldDescription.selector}</code></td>
                                <td>
                                    <#if fieldDescription.classifications?size == 0>
                                        <i>(none)</i>
                                    <#else>
                                        <#list fieldDescription.classifications as classification>
                                            <span class="badge badge-dark">${classification}</span>
                                        </#list>
                                    </#if>
                                </td>
                                <td class="text-links small">
                                    <#if fieldDescription.description?trim?length == 0>
                                        -----
                                    <#else>
                                        ${fieldDescription.description}
                                    </#if>
                                </td>
                            </tr>
                        </#list>
                    <#else>
                        <tr>
                            <td colspan="100">
                                <i>(no defined field descriptions)</i>
                            </td>
                        </tr>
                    </#if>
                </table>
            </div>
        </div>

        <#if gitStorageEnabled>
            <br/>
            <#assign historyUrl = appUrl.topics().showTopicHistory(topicName)>
            <#include "../git/entityHistoryContainer.ftl">
        </#if>

        <br/>
        <div class="card">
            <div class="card-header">
                <h4>Topic description in registry</h4>
                <span>File name: <span style="font-family: monospace;" id="filename"></span></span>
            </div>
            <div class="card-body p-1">
                <pre id="topic-yaml" data-topic-name="${topicName}"></pre>
            </div>
        </div>
    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
