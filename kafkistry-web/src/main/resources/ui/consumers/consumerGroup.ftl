<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="kafkaStreamsApp" type="com.infobip.kafkistry.service.kafkastreams.KafkaStreamsApp" -->
<#-- @ftlvariable name="shownTopic" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupIds" type="java.util.List<java.lang.String>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <meta name="clusterIdentifier" content="${clusterIdentifier}"/>
    <meta name="consumerGroupId" content="${consumerGroupId}"/>
    <script src="static/consumer/consumerGroup.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <h3><#include  "../common/backBtn.ftl"> Consumer group info </h3>

    <#include "components/groupMetadata.ftl">

    <#if consumerGroup??>
        <div class="card">
            <label class="card-header h4">Actions</label>
            <div class="card-body">
                <a class="btn btn-sm btn-outline-danger"
                   href="${appUrl.consumerGroups().showDeleteConsumerGroup(clusterIdentifier, consumerGroupId)}"
                >Delete consumer group...</a>
                <a class="btn btn-sm btn-outline-info"
                   href="${appUrl.consumerGroups().showResetConsumerGroupOffsets(clusterIdentifier, consumerGroupId)}"
                >Reset offsets...</a>
                <a class="btn btn-sm btn-outline-danger"
                   href="${appUrl.consumerGroups().showDeleteConsumerGroupOffsets(clusterIdentifier, consumerGroupId)}"
                >Delete offsets...</a>
                <div class="btn btn-sm btn-outline-primary mouse-pointer" id="clone-acton-btn">Clone...</div>

                <div id="clone-form" class="p-2" style="display: none;">
                    <div class="form-row form-group">
                        <input class="form-control col" type="search" name="otherConsumerGroup"
                               placeholder="Other consumer group id... (choose existing or enter new)" title="consumer group id">
                    </div>
                    <div class="form-row">
                        <label class="col-">
                            <a id="clone-from-btn" class="btn btn-sm btn-primary" href>Clone from &larr;</a>
                        </label>
                        <label class="col-">
                            <a id="clone-into-btn" class="btn btn-sm btn-primary" href>Clone into &rarr;</a>
                        </label>
                        <label class="col-">
                            <div id="cancel-clone-btn" class="mouse-pointer btn btn-sm btn-outline-secondary" href>Cancel</div>
                        </label>
                    </div>
                    <div style="display: none;">
                        <#list consumerGroupIds as cgId>
                            <div class="existing-consumer-group" data-consumer-group-id="${cgId}"></div>
                        </#list>
                    </div>
                </div>
            </div>
        </div>

        <br/>
        <#if kafkaStreamsApp??>
            <div class="card">
                <div class="card-header">
                    <span class="h4">KStream application:</span>
                    <a href="${appUrl.kStream().showKStreamApp(clusterIdentifier, kafkaStreamsApp.kafkaStreamAppId)}">
                        <button class="btn btn-sm btn-outline-dark mb-1" title="Inspect this group KStreams app">
                            ${kafkaStreamsApp.kafkaStreamAppId} 🔍
                        </button>
                    </a>
                </div>
            </div>
        </#if>

        <#assign affectingAcls = consumerGroup.affectingAclRules>
        <br/>
        <#include "../acls/affectingAcls.ftl">
        <br/>

        <div id="topics">
            <#assign shownTopic = (shownTopic??)?then(shownTopic, "")>
            <#if consumerGroup.topicMembers?size == 1>
                <#assign shownTopic = consumerGroup.topicMembers?first.topicName>
            </#if>
            <#list consumerGroup.topicMembers as topicMember>
                <#assign shown = (topicMember.topicName == shownTopic)>
                <#assign collapsedClass = shown?then("", "collapsed")>
                <#assign showClass = shown?then("show", "")>
                <div class="card">
                    <div class="card-header ${collapsedClass}" data-target="#topic-${topicMember?index}"
                         data-toggle="collapse">
                        <div class="float-left">
                            <span class="if-collapsed">▼</span>
                            <span class="if-not-collapsed">△</span>
                            <span>${topicMember.topicName}</span>
                            <span>
                                <a href="${appUrl.topics().showInspectTopicOnCluster(topicMember.topicName, clusterIdentifier)}">
                                    <button class="btn btn-sm btn-outline-secondary">Inspect 🔍</button>
                                </a>
                            </span>
                        </div>
                        <div class="float-right">
                            <@util.namedTypeStatusAlert type=topicMember.lag.status small=true/>
                            Total lag:${topicMember.lag.amount!'N/A'}
                            <#if topicMember.lag.percentage??>
                                <small title="Percentage of worst partition lag">
                                    <#if topicMember.lag.percentage?is_infinite>
                                        (<code class="small">(inf)</code>%)
                                    <#else>
                                        (${util.prettyNumber(topicMember.lag.percentage)}%)
                                    </#if>
                                </small>
                            </#if>
                        </div>
                    </div>
                    <div id="topic-${topicMember?index}" class="card-body p-0 collapse ${showClass}">
                        <table class="table table-sm m-0">
                            <thead class="thead-dark">
                            <tr>
                                <th>Partition</th>
                                <th></th>
                                <th class="text-right">
                                    Lag <@statusUtil.lagDoc/>
                                </th>
                                <th class="text-right">
                                    % <@statusUtil.lagPercentDoc/>
                                </th>
                                <th>Member client id</th>
                                <th>Member host</th>
                            </tr>
                            </thead>
                            <#list topicMember.partitionMembers as partition>
                                <tr>
                                    <td>${partition.partition}</td>
                                    <td>
                                        <@util.namedTypeStatusAlert type=partition.lag.status small=true/>
                                    </td>
                                    <td class="text-right">
                                        ${partition.lag.amount!'N/A'}
                                    </td>
                                    <td class="text-right">
                                        <#if partition.lag.percentage??>
                                            <#if partition.lag.percentage?is_infinite>
                                                <code class="small">(inf)</code>
                                            <#else>
                                                ${util.prettyNumber(partition.lag.percentage)}%
                                            </#if>
                                        <#else>
                                            N/A
                                        </#if>
                                    </td>
                                    <#if (partition.member)??>
                                        <td>${partition.member.clientId}</td>
                                        <td>${partition.member.host}</td>
                                    <#else>
                                        <td><i>(unassigned)</i></td>
                                        <td><i>(unassigned)</i></td>
                                    </#if>
                                </tr>
                            </#list>
                        </table>
                    </div>
                </div>
            </#list>
            <#if consumerGroup.topicMembers?size == 0>
                <div class="text-center">
                    <i>(no assigned topics to show)</i>
                </div>
            </#if>
        </div>
    </#if>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>