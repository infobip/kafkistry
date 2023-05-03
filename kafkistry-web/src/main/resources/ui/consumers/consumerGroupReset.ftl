<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/resetConsumer.js?ver=${lastCommit}"></script>
    <script src="static/consumer/resetForm.js?ver=${lastCommit}"></script>
    <script src="static/consumer/resetOptions.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <h3><#include  "../common/backBtn.ftl"> Consumer group offset reset</h3>

    <#include "components/groupMetadata.ftl">

    <#if consumerGroup??>
        <div class="alert alert-info">
            <strong>NOTE:</strong>
            All instances of consumers must not be active while performing offset reset
        </div>

        <#include "components/resetOptions.ftl">

        <br/>
        <div class="card">
            <div class="card-header">
                <div class="h4 m-0">Select topics/partitions to reset</div>
            </div>
            <div class="card-body p-0">
                <#include "components/groupTopicPartitions.ftl">
            </div>
        </div>
        <br/>
        <#if consumerGroup.status != "EMPTY" && consumerGroup.status != "UNKNOWN">
            <div class="alert alert-warning">
                <strong>WARNING:</strong>
                It appears that consumer group is still active, status: ${consumerGroup.status}. Reset won't work.
            </div>
        </#if>

        <#include "../common/cancelBtn.ftl">
        <button id="reset-offsets-btn" class="btn btn-sm btn-primary"
                data-cluster="${clusterIdentifier}" data-consumer-group="${consumerGroupId}">
            Reset selected offsets
        </button>

        <#include "../common/serverOpStatus.ftl">
    </#if>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>
