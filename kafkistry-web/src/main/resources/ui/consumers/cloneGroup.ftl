<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="fromConsumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="fromConsumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="intoConsumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="intoConsumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Clone consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/cloneConsumer.js?ver=${lastCommit}"></script>
    <script src="static/consumer/resetForm.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h3><#include  "../common/backBtn.ftl"> Consumer group offsets clone</h3>

    <div class="row">
        <div class="col-6">
            <div class="card">
                <div class="card-header h4">From</div>
                <div class="card-body">
                    <#assign consumerGroupId = fromConsumerGroupId>
                    <#if fromConsumerGroup??>
                        <#assign consumerGroup = fromConsumerGroup>
                    </#if>
                    <#include "components/groupMetadata.ftl">
                </div>
            </div>
        </div>
        <div class="col-6">
            <div class="card">
                <div class="card-header h4">Into</div>
                <div class="card-body">
                    <#assign consumerGroupId = intoConsumerGroupId>
                    <#if intoConsumerGroup??>
                        <#assign consumerGroup = intoConsumerGroup>
                    <#else>
                        <#assign newConsumerGroup = true>
                    </#if>
                    <#include "components/groupMetadata.ftl">
                </div>
            </div>
        </div>
    </div>

    <#if fromConsumerGroup??>
        <#if intoConsumerGroup??>
            <div class="alert alert-info">
                <strong>NOTE:</strong>
                All instances of consumers of '${intoConsumerGroupId}' must not be active while performing offset reset
            </div>
        </#if>
        <br/>

        <div class="card">
            <div class="card-header">
                <div class="h4 m-0">Select topics/partitions to clone</div>
            </div>
            <div class="card-body p-0">
                <#assign consumerGroup = fromConsumerGroup>
                <#include "components/groupTopicPartitions.ftl">
            </div>
        </div>
        <br/>
        <#if fromConsumerGroup.status != "EMPTY" && fromConsumerGroup.status != "UNKNOWN">
            <div class="alert alert-warning">
                <strong>WARNING:</strong>
                It appears that source consumer group to clone from is still active, status: ${fromConsumerGroup.status}.
                Offset values are changing while group actively consumes
            </div>
        </#if>
        <#if intoConsumerGroup?? && intoConsumerGroup.status != "EMPTY" && intoConsumerGroup.status != "UNKNOWN">
            <div class="alert alert-warning">
                <strong>WARNING:</strong>
                It appears that existing consumer group to clone into is still active, status: ${intoConsumerGroup.status}. Offsets clone won't work.
            </div>
        </#if>

        <#include "../common/cancelBtn.ftl">
        <button id="clone-offsets-btn" class="btn btn-sm btn-primary"
                data-cluster="${clusterIdentifier}"
                data-from-consumer-group="${fromConsumerGroupId}"
                data-into-consumer-group="${intoConsumerGroupId}"
                title="'${fromConsumerGroupId}' &rarr; '${intoConsumerGroupId}'">
            Clone selected offsets
        </button>

        <#include "../common/serverOpStatus.ftl">

        <br/>
        <div id="into-group-link" style="display: none;">
            Open inspect:
            <a class="btn btn-sm btn-outline-dark"
               href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, intoConsumerGroupId)}">
                ${intoConsumerGroupId} @ ${clusterIdentifier}
            </a>
        </div>
    </#if>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>