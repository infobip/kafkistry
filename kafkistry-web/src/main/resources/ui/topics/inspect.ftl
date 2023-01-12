<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicName"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="topicStatus"  type="com.infobip.kafkistry.service.topic.TopicClusterStatus" -->
<#-- @ftlvariable name="expectedTopicInfo"  type="com.infobip.kafkistry.service.topic.ExpectedTopicInfo" -->
<#-- @ftlvariable name="wrongPartitionValues"  type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="clusterInfo"  type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="topicConsumerGroups" type="java.util.List<com.infobip.kafkistry.service.consumers.KafkaConsumerGroup>" -->
<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->
<#-- @ftlvariable name="partitionReAssignments" type="java.util.Map<Partition, com.infobip.kafkistry.kafka.TopicPartitionReAssignment>" -->
<#-- @ftlvariable name="topicResources" type="com.infobip.kafkistry.service.resources.TopicDiskUsage" -->
<#-- @ftlvariable name="kStreamsInvolvement" type="com.infobip.kafkistry.service.kafkastreams.TopicKStreamsInvolvement" -->
<#-- @ftlvariable name="inspectExtensionProperties" type="com.infobip.kafkistry.webapp.TopicInspectExtensionProperties" -->
<#-- @ftlvariable name="autopilotEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="autopilotActions"  type="java.util.List<com.infobip.kafkistry.autopilot.repository.ActionFlow>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/verifyReAssignments.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/runPreferredReplicaElection.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/cancelReAssignments.js?ver=${lastCommit}"></script>
    <#if inspectExtensionProperties.jsName??>
        <script src="static/topic/${inspectExtensionProperties.jsName}.js?ver=${lastCommit}"></script>
    </#if>
    <title>Kafkistry: Topic on cluster insect</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util_>
<#import "../common/documentation.ftl" as doc_>
<#import "../common/infoIcon.ftl" as info_>
<#import "../consumers/util.ftl" as consumerUtil>
<#import "../sql/sqlQueries.ftl" as sql>

<div class="container">

    <h3><#include  "../common/backBtn.ftl"> Topic on cluster inspection </h3>

    <table class="table table-sm">
        <tr>
            <th>Topic</th>
            <td>
                <#assign statusTypes = util_.namedTypeListToStringList(topicStatus.status.types)>
                <#assign presentInRegistry = !statusTypes?seq_contains("UNKNOWN")>
                <#if presentInRegistry>
                    <a href="${appUrl.topics().showTopic(topicName)}">${topicName}</a>
                <#else>
                    ${topicName}
                </#if>
            </td>
        </tr>
        <tr>
            <th>Cluster</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
            </td>
        </tr>
        <tr>
            <th>Last refresh</th>
            <td class="time" data-time="${topicStatus.lastRefreshTime?c}"></td>
        </tr>
        <tr>
            <th>Topic status on cluster</th>
            <td>
                <#assign topicOnClusterStatus = topicStatus.status>
                <#include "../common/topicOnClusterStatus.ftl">
            </td>
        </tr>
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
            <th>Action</th>
            <td>
                <#assign availableActions = topicStatus.status.availableActions>
                <#include "../common/topicOnClusterAction.ftl">
            </td>
        </tr>
        <tr>
            <th>Utilities</th>
            <td>
                <a href="${appUrl.compare().showComparePage(topicName, clusterIdenitifier, "ACTUAL")}"
                   class="btn btn-outline-info btn-sm">Compare...</a>
                <a href="${appUrl.consumeRecords().showConsumePage(topicName, clusterIdentifier)}"
                   class="btn btn-outline-info btn-sm">Consume...</a>
                <a href="${appUrl.recordsStructure().showTopicStructurePage(topicName, clusterIdentifier)}"
                   class="btn btn-outline-info btn-sm">Records structure...</a>
            </td>
        </tr>
    </table>

    <#if topicOffsets??>
        <#include "topicOffsetsStatus.ftl">
    </#if>

    <br/>

    <#if topicStatus.existingTopicInfo?? && clusterInfo??>
        <#if (expectedTopicInfo.resourceRequirements)??>
            <#import "topicResourceUsages.ftl" as usages>
            <div class="card">
                <div class="card-header h4">Resource requirements</div>
                <div class="card-body p-0">
                    <#assign requirements=expectedTopicInfo.resourceRequirements>
                    <table class="table table-sm table-bordered">
                        <thead class="thead-dark">
                            <tr>
                                <th>Avg message size</th>
                                <th>Messages rate</th>
                                <th>Retention</th>
                            </tr>
                        </thead>
                        <tr>
                            <td>
                                <#assign msgSize = requirements.avgMessageSize>
                                ${msgSize.amount} ${msgSize.unit}
                            </td>
                            <td>
                                <#assign messagesRate = requirements.messagesRate>
                                <#if requirements.messagesRateOverrides?keys?seq_contains(clusterIdentifier)>
                                    <#assign messagesRate = requirements.messagesRateOverrides[clusterIdentifier]>
                                </#if>
                                ${messagesRate.amount}
                                <#switch messagesRate.factor>
                                    <#case "ONE"><#break>
                                    <#case "K">thousand<#break>
                                    <#case "M">million<#break>
                                    <#case "G">billion<#break>
                                </#switch>
                                messages /
                                <#switch messagesRate.unit>
                                    <#case "MSG_PER_DAY">day<#break>
                                    <#case "MSG_PER_HOUR">hour<#break>
                                    <#case "MSG_PER_MINUTE">min<#break>
                                    <#case "MSG_PER_SECOND">sec<#break>
                                </#switch>
                            </td>
                            <td>
                                <#assign retention = requirements.retention>
                                ${retention.amount} ${retention.unit}
                            </td>
                        </tr>
                    </table>
                    <table class="table table-bordered table-sm m-0">
                        <thead class="thead-dark">
                        <tr><@usages.usageHeaderSectionCells/></tr>
                        <tr><@usages.usageHeaderCells/></tr>
                        </thead>
                        <tr>
                            <@usages.usageValuesCells optionalResourceRequiredUsages=topicStatus.resourceRequiredUsages/>
                        </tr>
                    </table>
                </div>
            </div>
            <br/>
        </#if>

        <#if topicResources??>
            <#include "resources.ftl">
            <br/>
        </#if>

        <div class="card">
        <div class="card-header h4">Topic configuration</div>
        <div class="card-body p-0">
        <table class="table table-sm table-bordered">
            <thead class="thead-dark">
            <tr>
                <th>Property</th>
                <th>Actual value</th>
                <th>Expected value</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <#assign wrong = wrongPartitionValues?seq_contains("partition-count")>
                <td>Number partitions</td>
                <td <#if wrong>class="value-mismatch"</#if>>${topicStatus.existingTopicInfo.properties.partitionCount}</td>
                <td <#if wrong>class="value-mismatch"</#if>>
                    <#if expectedTopicInfo??>
                        ${expectedTopicInfo.properties.partitionCount}
                    <#else>
                        ---
                    </#if>
                </td>
            </tr>
            <tr>
                <#assign wrong = wrongPartitionValues?seq_contains("replication-factor")>
                <td>Replication factor</td>
                <td <#if wrong>class="value-mismatch"</#if>>${topicStatus.existingTopicInfo.properties.replicationFactor}</td>
                <td <#if wrong>class="value-mismatch"</#if>>
                    <#if expectedTopicInfo??>
                        ${expectedTopicInfo.properties.replicationFactor}
                    <#else>
                        ---
                    </#if>
                </td>
            </tr>
            </tbody>
        </table>

        <#assign config = topicStatus.existingTopicInfo.config>
        <#assign configEntryStatuses = topicStatus.configEntryStatuses!{}>
        <#assign expectedConfig = (expectedTopicInfo.config)!{}>
        <#assign showExpected = true>
        <#include "../common/existingConfig.ftl">
        </div>
        </div>

        <br/>
        <div class="card">
            <div class="card-header">
                <span class="h4">Consumer groups (${topicConsumerGroups?size})</span>
            </div>
            <div class="card-body p-1">
                <#if topicConsumerGroups?size == 0>
                    <i>(no consumer groups reading from this topic)</i>
                <#else>
                    <table class="table table-sm mb-0">
                        <thead class="thead-dark">
                        <tr>
                            <th style="width: 60%">Group</th>
                            <th>Status</th>
                            <th>Lag</th>
                            <th class="text-right"><@consumerUtil.lagDoc/></th>
                            <th class="text-right"><@consumerUtil.lagPercentDoc/></th>
                        </tr>
                        </thead>
                        <#list topicConsumerGroups as consumerGroup>
                            <tr>
                                <td>
                                    <a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroup.groupId, topicName)}">
                                        ${consumerGroup.groupId}
                                    </a>
                                </td>
                                <td>
                                    <@util.namedTypeStatusAlert type=consumerGroup.status small=true/>
                                </td>
                                <#list consumerGroup.topicMembers as topicMember>
                                    <#if topicMember.topicName == topicName>
                                        <td>
                                            <@util.namedTypeStatusAlert type=topicMember.lag.status small=true/>
                                        </td>
                                        <td class="text-right">
                                            ${topicMember.lag.amount!'N/A'}
                                        </td>
                                        <td class="text-right">
                                            <#if topicMember.lag.percentage??>
                                                <small title="Percentage of worst partition lag">
                                                    <#if topicMember.lag.percentage?is_infinite>
                                                        (<code class="small">(inf)</code>%)
                                                    <#else>
                                                        (${util.prettyNumber(topicMember.lag.percentage)}%)
                                                    </#if>
                                                </small>
                                            </#if>
                                        </td>
                                    </#if>
                                </#list>
                            </tr>
                        </#list>
                    </table>
                </#if>
            </div>
        </div>

        <br/>
        <div class="card">
            <#assign numKStreamApps = kStreamsInvolvement.inputOf?size + ((kStreamsInvolvement.internalIn)??)?then(1, 0)>
            <div class="card-header">
                <span class="h4">KStream applications (${numKStreamApps})</span>
            </div>
            <div class="card-body p-1">
                <#if kStreamsInvolvement.inputOf?size == 0 && !(kStreamsInvolvement.internalIn)??>
                    <i>(not involved in KStreams)</i>
                <#else>
                    <table class="table table-sm mb-0">
                        <thead class="thead-dark">
                        <tr>
                            <th>KStream App</th>
                            <th>This topic involved as</th>
                        </tr>
                        </thead>
                        <#list kStreamsInvolvement.inputOf as kStreamApp>
                            <tr>
                                <td>
                                    <a href="${appUrl.kStream().showKStreamApp(clusterIdentifier, kStreamApp.kafkaStreamAppId)}">
                                        ${kStreamApp.kafkaStreamAppId}
                                    </a>
                                </td>
                                <td><span class="badge badge-primary">INPUT</span></td>
                            </tr>
                        </#list>
                        <#if kStreamsInvolvement.internalIn??>
                            <#assign kStreamApp = kStreamsInvolvement.internalIn>
                            <tr>
                                <td>
                                    <a href="${appUrl.kStream().showKStreamApp(clusterIdentifier, kStreamApp.kafkaStreamAppId)}">
                                        ${kStreamApp.kafkaStreamAppId}
                                    </a>
                                </td>
                                <td><span class="badge badge-info">INTERNAL</span></td>
                            </tr>
                        </#if>
                    </table>
                </#if>
            </div>
        </div>

        <br/>
        <#assign affectingAcls = topicStatus.status.affectingAclRules>
        <#include "../acls/affectingAcls.ftl">

        <#if inspectExtensionProperties.templateName??>
            <#assign nameTemplate = '<#include "${inspectExtensionProperties.templateName}.ftl">'?interpret>
            <@nameTemplate/>
        </#if>

        <br/>
        <#include "partitionStats.ftl">

        <br/>
        <#assign partitionsAssignments = topicStatus.existingTopicInfo.partitionsAssignments>
        <#assign assignmentsDisbalance = topicStatus.existingTopicInfo.assignmentsDisbalance>
        <#if (topicOffsets.partitionsOffsets)??>
            <#assign partitionOffsets = topicOffsets.partitionsOffsets>
        </#if>
        <#include "partitionReplicaAssignments.ftl">
        <button id="verify-reassignments-btn" data-topic-name="${topicName}"
                data-cluster-identifier="${clusterInfo.identifier}" class="btn btn-info btn-sm">
            Execute verify re-assignments <@info_.icon tooltip=doc_.verifyReAssignmentsBtn/>
        </button>
        <button id="run-preferred-replica-election-btn" data-topic-name="${topicName}"
                data-cluster-identifier="${clusterInfo.identifier}" class="btn btn-info btn-sm">
            Execute preferred replica election <@info_.icon tooltip=doc_.preferedReplicaLeaderElectionBtn/>
        </button>
        <#assign reAssignmentInProgress = partitionReAssignments?? && partitionReAssignments?size gt 0>
        <#if reAssignmentInProgress>
            <@sql.topicReassignmeentProgress cluster=clusterInfo.identifier topic=topicName/>
            <a href="${appUrl.clustersManagement().showApplyThrottling(clusterInfo.identifier)}"
                class="btn btn-sm btn-outline-info">
                Change throttle rate...
            </a>
        </#if>
        <br/>
        <#assign noAutoBack = true>
        <#include "../common/serverOpStatus.ftl">
        <br/>

        <#if reAssignmentInProgress>
            <div>
                <button class="btn btn-outline-danger btn-sm mb-2 collapsed" data-toggle="collapsing" data-target="#cancel-re-assignment-menu">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    Cancel in-progress re-assignments...
                </button>
                <div id="cancel-re-assignment-menu" class="collapse">
                    <button id="cancel-reassignments-btn" data-topic-name="${topicName}"
                            data-cluster-identifier="${clusterInfo.identifier}" class="btn btn-danger btn-sm">
                        Do cancel re-assignment of ${partitionReAssignments?size} partitions
                    </button>
                    <button class="btn btn-outline-secondary btn-sm" data-toggle="collapsing" data-target="#cancel-re-assignment-menu">
                        Don't cancel
                    </button>
                </div>
            </div>
            <br/>
        </#if>

        <div>
            <div>
                <button class="btn btn-outline-info btn-sm mb-2 collapsed" data-toggle="collapsing" data-target="#unwanted-leader-menu">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    Re-assign by setting unwanted preferred leader...
                </button>
            </div>
            <div id="unwanted-leader-menu" class="collapse">
                <span>Choose unwanted broker: </span>
                <#list clusterInfo.nodeIds as brokerId>
                    <a class="btn btn-sm btn-warning" href="${appUrl.topicsManagement().showTopicUnwantedLeaderReAssignment(topicName, clusterIdentifier, brokerId)}">
                        ${brokerId?c}
                    </a>
                </#list>
                <button class="btn btn-outline-secondary btn-sm" data-toggle="collapsing" data-target="#unwanted-leader-menu">
                    Cancel
                </button>
            </div>
        </div>
        <br/>

        <div>
            <div>
                <button class="btn btn-outline-info btn-sm mb-2 collapsed" data-toggle="collapsing" data-target="#excluded-brokers-menu">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    Re-assign by excluding brokers from assignment...
                </button>
            </div>
            <div id="excluded-brokers-menu" class="collapse">
                <span>Choose excluded broker(s): </span>
                <br/>
                <table>
                    <tr>
                        <#list clusterInfo.nodeIds as brokerId>
                            <td>
                                <a class="btn btn-sm btn-outline-danger m-1" href="${appUrl.topicsManagement().showTopicExcludedBrokersReAssignment(topicName, clusterIdentifier, [brokerId])}">
                                    ${brokerId?c}
                                </a>
                            </td>
                        </#list>
                    </tr>
                        <#list clusterInfo.nodeIds as b1>
                            <tr>
                                <#list clusterInfo.nodeIds as b2>
                                    <td>
                                        <#if b1 != b2>
                                            <a class="btn btn-sm btn-outline-danger m-1" href="${appUrl.topicsManagement().showTopicExcludedBrokersReAssignment(topicName, clusterIdentifier, [b1, b2])}">
                                                ${b1?c}+${b2?c}
                                            </a>
                                        </#if>
                                    </td>
                                </#list>
                            </tr>
                        </#list>
                </table>
                <button class="btn btn-outline-secondary btn-sm" data-toggle="collapsing" data-target="#excluded-brokers-menu">
                    Cancel
                </button>
            </div>
        </div>
        <br/>

        <div class="form-row">
            <div class="col-">
                <a class="btn btn-outline-info btn-sm" href="${appUrl.topicsManagement().showCustomReAssignmentInput(topicName, clusterIdentifier)}">
                    Specify custom assignments...
                </a>
            </div>
            <div class="col-">
                <a class="btn btn-outline-info btn-sm" href="${appUrl.topicsManagement().showTopicConfigSet(topicName, clusterIdentifier)}">
                    Set config...
                </a>
            </div>
            <div class="col-">
                <a class="btn btn-outline-danger btn-sm" href="${appUrl.topicsManagement().showDeleteTopicOnCluster(topicName, clusterInfo.identifier)}">
                    Force delete...
                </a>
            </div>
        </div>

        <br/>
        <br/>
    <#else>
        <p>
            <i>(no data for selected cluster)</i>
        </p>
    </#if>

    <p><#include  "../common/backBtn.ftl"></p>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>