<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->
<#-- @ftlvariable name="allTopicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/resetForm.js?ver=${lastCommit}"></script>
    <script src="static/consumer/resetOptions.js?ver=${lastCommit}"></script>
    <script src="static/consumer/presetOffsets.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <h3><#include  "../common/backBtn.ftl"> Consumer group offset preset/init</h3>

    <#assign newConsumerGroup = true>
    <#include "components/groupMetadata.ftl">

    <#if consumerGroup??>
        <div class="alert alert-info">
            <strong>NOTE:</strong>
            All instances of consumers must not be active while performing offset reset
        </div>
    </#if>

    <#include "components/resetOptions.ftl">

    <br/>
    <div class="card">
        <div class="card-header">
            <div class="h4 m-0">Select topics to preset offsets for</div>
        </div>
        <div class="card-body pr-0 pl-0">
            <table class="table table-sm datatable">
                <thead class="thead-dark">
                <tr>
                    <th></th>
                    <th>Topic</th>
                    <th>Status</th>
                </tr>
                </thead>
                <#list allTopicsOffsets as topic,offsets>
                    <#assign haveCommits = topicsOffsets?keys?seq_contains(topic)>
                    <tr class="topic">
                        <td>
                            <input type="checkbox" class="form-control topic-selector"
                                   data-topic="${topic}" title="Include/exclude topic"/>
                        </td>
                        <td>${topic}</td>
                        <td>
                            <#if haveCommits>
                                <span class="badge badge-primary">HAVE COMMITS</span>
                            <#else>
                                <span class="badge badge-secondary">NO COMMITS</span>
                            </#if>
                        </td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
    <br/>

    <#if consumerGroup??>
        <#if consumerGroup.status != "EMPTY" && consumerGroup.status != "UNKNOWN">
            <div class="alert alert-warning">
                <strong>WARNING:</strong>
                It appears that consumer group is still active, status: ${consumerGroup.status}. Reset won't work.
            </div>
        </#if>
    </#if>

    <#include "../common/cancelBtn.ftl">
    <button id="preset-offsets-btn" class="btn btn-sm btn-primary"
            data-cluster="${clusterIdentifier}" data-consumer-group="${consumerGroupId}">
        Preset selected offsets for <span id="topic-count">0</span> topic(s)
    </button>

    <#include "../common/serverOpStatus.ftl">

    <br/>
    <div id="preset-group-link" style="display: none;">
        Open inspect:
        <a class="btn btn-sm btn-outline-dark"
           href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroupId)}">
            ${consumerGroupId} @ ${clusterIdentifier}
        </a>
    </div>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>
