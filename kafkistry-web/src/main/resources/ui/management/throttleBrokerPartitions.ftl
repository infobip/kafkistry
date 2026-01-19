<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="throttleBrokerTopicPartitionsSuggestion" type="com.infobip.kafkistry.service.topic.ThrottleBrokerTopicPartitionsSuggestion" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/throttlePartitionBrokers.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/replicationThrottle.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Throttle replication</title>
    <meta name="clusterIdentifier" content="${clusterInfo.identifier}">
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1><#include "../common/backBtn.ftl"> Cluster global throttling from/into specific broker(s)</h1>

    <table class="table table-hover">
        <tr>
            <th>Cluster</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterInfo.identifier)}">${clusterInfo.identifier}</a>
            </td>
        </tr>
        <tr>
            <th>All brokers</th>
            <td>
                <#include "../clusters/clusterNodesList.ftl">
            </td>
        </tr>
        <tr>
            <th>Throttled brokers</th>
            <td>
                <#list throttleBrokerTopicPartitionsSuggestion.throttleRequest.brokerIds as brokerId>
                    <@clusterNodeId nodeId=brokerId/>
                </#list>
            </td>
        </tr>
    </table>

    <hr/>

    <div class="card">
        <div class="card-header">
            <span class="h4">Generated topic partition broker throttled replicas</span>
        </div>
        <div class="card-body">
            <#list throttleBrokerTopicPartitionsSuggestion.throttleRequest.topicNames as topic>
                <#assign topicUrl = appUrl.topics().showInspectTopicOnCluster(topic, clusterInfo.identifier)>
                <#if (throttleBrokerTopicPartitionsSuggestion.topicThrottleConfigs[topic])??>
                    <#assign throttleConfigs = throttleBrokerTopicPartitionsSuggestion.topicThrottleConfigs[topic]>
                    <div class="topic-throttle" data-topic="${topic}">
                        <p><strong>Topic (${topic?index + 1}):</strong> <a href="${topicUrl}">${topic}</a></p>
                        <ul>
                            <#list throttleConfigs as configKey, configValue>
                                <li class="config-entry" data-configKey="${configKey}" data-configValue="${configValue}">
                                    ${configKey}: ${configValue}
                                </li>
                            </#list>
                        </ul>
                        <#assign statusId = "topic-${topic}">
                        <#include "../common/serverOpStatus.ftl">
                    </div>
                <#else>
                    <div class="no-topic-throttle" data-topic="${topic}">
                        <p><strong>Topic (${topic?index + 1}):</strong> <a href="${topicUrl}">${topic}</a></p>
                        <div class="alert alert-secondary">
                            Nothing to throttle
                            <#assign noThrotteTooltip>
                                Topic's partition assignments are assigned in a such way that when throttling
                                broker ids [${throttleBrokerTopicPartitionsSuggestion.throttleRequest.brokerIds?join(", ")}] results
                                in no combination leader-follower to be affected. Therefore, nothing needs to be throttled at this topic level.
                            </#assign>
                            <@info.icon tooltip=noThrotteTooltip/>
                        </div>
                    </div>
                </#if>
                <#if !topic?is_last>
                    <hr/>
                </#if>
            </#list>
        </div>
    </div>
    <br/>

    <table class="table table-hover">
        <thead class="table-theme-dark">
        <tr>
            <th colspan="100" class="text-center">Total maximum data migration if throttled brokers were totally
                out-of-sync
            </th>
        </tr>
        </thead>
        <tr>
            <th>Throttled brokers</th>
            <td>
                <#list throttleBrokerTopicPartitionsSuggestion.throttleRequest.brokerIds as brokerId>
                    <@clusterNodeId nodeId=brokerId/>
                </#list>
            </td>
        </tr>
        <#assign dataMigration = throttleBrokerTopicPartitionsSuggestion.totalMaximumDataMigration>
        <#include "assignmentDataMigration.ftl">
    </table>

    <#assign maxBrokerIOBytes = throttleBrokerTopicPartitionsSuggestion.totalMaximumDataMigration.maxBrokerIOBytes>
    <#include "replicationThrottle.ftl">
    <br/>

    <button id="apply-throttling-brokers-btn" class="btn btn-primary btn-sm">
        Apply throttles
    </button>
    <#include "../common/cancelBtn.ftl">

    <div class="data" style="display: none;">
        <#assign throttledBrokers = throttleBrokerTopicPartitionsSuggestion.throttleRequest.brokerIds>
        <#assign leaderBrokers = []>
        <#assign onlineBrokers = clusterInfo.onlineNodeIds>
        <#assign offlineBrokers = []>
        <#list clusterInfo.brokerIds as brokerId>
            <#if !throttledBrokers?seq_contains(brokerId)>
                <#assign leaderBrokers = leaderBrokers + [brokerId]>
            </#if>
            <#if !onlineBrokers?seq_contains(brokerId)>
                <#assign offlineBrokers = offlineBrokers + [brokerId]>
            </#if>
        </#list>
        <div id="leader-brokers">
            <#list leaderBrokers as brokerId>
                <div class="broker-id" data-brokerId="${brokerId?c}"></div>
            </#list>
        </div>
        <div id="throttled-brokers">
            <#list throttledBrokers as brokerId>
                <div class="broker-id" data-brokerId="${brokerId?c}"></div>
            </#list>
        </div>
        <div id="offline-brokers">
            <#list offlineBrokers as brokerId>
                <div class="broker-id" data-brokerId="${brokerId?c}"></div>
            </#list>
        </div>
    </div>

    <div id="throttle-setup-progress" class="card mt-4" style="display: none;">
        <div class="card-header">
            <span class="h4">Throttle setup progress</span>
        </div>
        <div class="card-body">
            <#assign noAutoBack = true>
            <div class="row">
                <div class="col-3">
                    <span class="font-weight-bold">Leader brokers</span><br/>
                    <span class="small font-italic">(throttling replication from)</span>
                </div>
                <div class="col">
                    <#assign statusId = "leader-brokers">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
            <div class="row">
                <div class="col-3 font-weight-bold">Topics config</div>
                <div class="col">
                    <#assign statusId = "topics">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
            <div class="row">
                <div class="col-3">
                    <span class="font-weight-bold">Throttled brokers</span><br/>
                    <span class="small font-italic">(throttling replication into)</span>
                </div>
                <div class="col">
                    <#assign statusId = "throttled-brokers">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
            <div id="back-to-cluster-btn" class="row" style="display: none;">
                <div class="col">
                    <a class="btn btn-sm btn-outline-secondary"
                       href="${appUrl.clusters().showCluster(clusterInfo.identifier)}">
                        Back to cluster
                    </a>
                    or
                    <a href="${appUrl.topicsManagement().showBulkVerifyReAssignments(clusterInfo.identifier)}"
                       class="btn btn-sm btn-outline-info">
                        Remove throttle...
                    </a>
                    once it's not needed anymore
                </div>
            </div>
        </div>
    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>