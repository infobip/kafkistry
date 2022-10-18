<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/consumerGroupDelete.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <h3>Delete consumer group on cluster</h3>
    <table class="table">
        <thead class="thead-dark">
        <tr>
            <th>Cluster</th>
            <th>Group</th>
            <th>Status</th>
            <th>Lag</th>
            <th>Num topics</th>
        </tr>
        </thead>
        <tr>
            <td>${clusterIdentifier}</td>
            <td>${consumerGroupId}</td>
            <td>
                <div class="alert alert-inline alert-sm mb-0 ${statusUtil.consumerStatusAlertClass(consumerGroup.status)}">
                    ${consumerGroup.status}
                </div>
            </td>
            <td>
                <@util.namedTypeStatusAlert type=consumerGroup.lag.status/>
            </td>
            <td>${consumerGroup.topicMembers?size}</td>
        </tr>
    </table>

    <div class="alert alert-danger">
        <strong style="color: red;">Warning</strong>:
        Consumer group delete will cause all offsets commit information to be lost
    </div>

    <p>
        <label>
            Confirm your action by entering DELETE here:
            <input type="text" id="delete-confirm">
        </label>
    </p>

    <#include "../common/cancelBtn.ftl">
    <button id="deleteConsumerGroupBtn" class="btn btn-sm btn-outline-danger" data-clusterIdentifier="${clusterIdentifier}"
            data-consumerGroupId="${consumerGroupId}">
        Delete consumer group
    </button>

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>