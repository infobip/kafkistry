<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topics" type="java.util.List<com.infobip.kafkistry.service.topic.ClusterTopicStatus>" -->
<#-- @ftlvariable name="topicsReplicas" type="java.util.Map<java.lang.String, com.infobip.kafkistry.kafkastate.TopicReplicaInfos>" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/throttlePartitionBrokersForm.js?ver=${lastCommit}"></script>
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
            <th>Brokers</th>
            <td>
                <#include "../clusters/clusterNodesList.ftl">
            </td>
        </tr>
    </table>

    <hr/>

    <#-- to prevent user going back seeing partially selected inputs, better to have clean start than inconsistent-->
    <form autocomplete="off">

    <div class="form table">
        <div class="row g-2">
            <div class="col-2">
                Select broker(s)
            </div>
            <div class="col">
                <select class="form-control" name="brokerIds" title="Select brokers to throttle..." multiple>
                    <#list clusterInfo.nodeIds as brokerId>
                        <option value="${brokerId?c}">${brokerId?c}</option>
                    </#list>
                </select>
            </div>
        </div>
    </div>

    <div style="display: none;">
        <#list topics as topic>
            <div class="topic-name" data-topic="${topic.topicName}"></div>
        </#list>
    </div>

    <div class="card">
        <div class="card-header">
            <div class="d-flex align-items-center gap-2" id="topic-select-type">
                <h5>Select topics</h5>
                <label class="btn btn-outline-primary m-1 active">
                    All <input type="radio" name="topicSelectType" value="ALL" checked>
                </label>
                <label  class="btn btn-outline-success m-1">
                    Custom <input type="radio" name="topicSelectType" value="CUSTOM">
                </label>
                <h5>(<span id="selected-topics-count">0</span>)</h5>
            </div>
        </div>
        <div id="topics-multi-select" class="card-body pl-0 pr-0" style="display: none;">
            <table id="topics-table" class="table table-hover datatable">
                <thead class="table-theme-dark">
                <tr>
                    <th>Topic</th>
                    <th>Size</th>
                    <th>Rate</th>
                </tr>
                </thead>
                <#list topics as topic>
                    <tr>
                        <td>
                            <label class="form-check">
                                <input type="checkbox" class="topic-checkbox mouse-pointer form-check-input"
                                       title="select/unselect" data-topic="${topic.topicName}">
                                <span class="form-check-label">${topic.topicName}</span>
                            </label>
                        </td>
                        <#if (topicsReplicas[topic.topicName].totalSizeBytes)??>
                            <#assign sizeBytes = topicsReplicas[topic.topicName].totalSizeBytes>
                            <td data-order="${sizeBytes?c}">
                                ${util.prettyDataSize(sizeBytes)}
                            </td>
                        <#else>
                            <td data-order="-1">N/A</td>
                        </#if>
                        <#if (topicsOffsets[topic.topicName].messagesRate)??>
                            <#assign messagesRate = topicsOffsets[topic.topicName].messagesRate.longestRangeRate()>
                            <td data-order="${messagesRate?c}">
                                ${util.prettyNumber(messagesRate)} msg/sec
                            </td>
                        <#else>
                            <td data-order="-1">N/A</td>
                        </#if>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
    </form>
    <br/>

    <button id="continue-throttle-btn" class="btn btn-info btn-sm">
        Continue...
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>