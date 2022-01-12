<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="topics" type="java.util.List<com.infobip.kafkistry.service.ClusterTopicStatus>" -->

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

    <table class="table">
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

    <div class="form table">
        <div class="form-row">
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
        <div class="form-row mt-3">
            <div class="col-2">
                Select topics
            </div>
            <div class="col">
                <div class="form-row form-inline" id="topic-select-type">
                    <label class="btn btn-outline-primary form-control m-1 active">
                        All <input type="radio" name="topicSelectType" value="ALL" checked>
                    </label>
                    <label  class="btn btn-outline-success form-control m-1">
                        Only <input type="radio" name="topicSelectType" value="ONLY">
                    </label>
                    <label class="btn btn-outline-danger form-control m-1">
                        Not <input type="radio" name="topicSelectType" value="NOT">
                    </label>
                </div>
                <div class="form-row" id="topics-multi-select" style="display: none;">
                    <select name="topics" class="form-control" title="select topics..." multiple="multiple"
                            data-live-search="true" data-size="6">
                        <#list topics as topic>
                            <option value="${topic.topicName}">${topic.topicName}</option>
                        </#list>
                    </select>
                </div>
            </div>
        </div>
    </div>

    <button id="continue-throttle-btn" class="btn btn-info btn-sm">
        Continue...
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>