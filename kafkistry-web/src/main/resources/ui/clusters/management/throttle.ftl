<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="commonThrottle" type="com.infobip.kafkistry.kafka.ThrottleRate" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <meta name="cluster-identifier" content="${clusterInfo.identifier}">
    <script src="static/cluster/throttle.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Cluster</title>
</head>

<body>


<#include "../../commonMenu.ftl">

<#import "../../common/util.ftl" as util>
<#import "../clusterNodesList.ftl" as broker>

<#macro throttleRow brokerId throttle>
    <#if brokerId?is_number>
        <#assign brokerIdStr = "${brokerId?c}">
    <#else>
        <#assign brokerIdStr = "${brokerId}">
    </#if>

    <tr class="throttle-row">
        <th>
            <#if brokerId?is_string>
                <div class="alert alert-sm alert-inline m-0 alert-secondary">${brokerId}</div>
            <#else>
                <@broker.clusterNodeId nodeId=brokerId/>
            </#if>
        </th>

        <td colspan="3">
            <label class="m-0 width-full">
                <input type="text" class="conf-value-in form-control" name="leader.rate" value="${(throttle.leaderRate?c)!("")}" data-nan-default="no throttle"/>
                <span class="small text-primary conf-value-out"></span>
            </label>
        </td>
        <td colspan="3">
            <label class="m-0 width-full">
                <input type="text" class="conf-value-in form-control" name="follower.rate" value="${(throttle.followerRate?c)!("")}" data-nan-default="no throttle"/>
                <span class="small text-primary conf-value-out"></span>
            </label>
        </td>
        <td colspan="3">
            <label class="m-0 width-full">
                <input type="text" class="conf-value-in form-control" name="alterDirIo.rate" value="${(throttle.alterDirIoRate?c)!("")}" data-nan-default="no throttle"/>
                <span class="small text-primary conf-value-out"></span>
            </label>
        </td>
        <td>
            <button class="apply-throttle-btn btn btn-sm btn-primary" data-brokerId="${brokerIdStr}">Apply</button>
        </td>
    </tr>
    <tr class="no-hover">
        <td colspan="11">
            <#assign statusId = "broker-${brokerIdStr}">
            <#include "../../common/serverOpStatus.ftl">
        </td>
    </tr>
</#macro>


<div class="container">
    <#assign clusterIdentifier = clusterInfo.identifier>
    <h3><#include "../../common/backBtn.ftl"> Set cluster throttle rate</h3>
    <hr/>

    <p><strong>Cluster:</strong> ${clusterIdentifier}</p>

    <table class="table fixed-layout">
        <thead class="thead-dark">
        <tr>
            <th>Broker</th>
            <th colspan="3">Leader rate</th>
            <th colspan="3">Follower rate</th>
            <th colspan="3">Alter dir rate</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <@throttleRow brokerId="ALL" throttle=commonThrottle/>
        <#list clusterInfo.brokerIds as brokerId>
            <@throttleRow brokerId=brokerId throttle=(clusterInfo.perBrokerThrottle?api.get(brokerId))!{}/>
        </#list>
        </tbody>

    </table>

    <#include "../../common/cancelBtn.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>
