<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Cluster resources</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../commonMenu.ftl">
<#import "../sql/sqlQueries.ftl" as sql>

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<#assign hasOrphaned = clusterResources.combined.usage.orphanedReplicasCount gt 0>
<#assign showReplicas = !hasOrphaned>

<div class="container">
    <h3><#include "../common/backBtn.ftl"> Cluster resources usage</h3>
    <br/>

    <table class="table table-hover mt-2">
        <tr>
            <th>Cluster identifier</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
            </td>
        </tr>
        <tr>
            <th>Inspect</th>
            <td>
                <@sql.diskUsagePerBroker cluster=clusterIdentifier/>
                <@sql.topClusterTopicReplicas cluster=clusterIdentifier/>
                <br/>
                <br/>
                <@sql.topClusterTopicPossibleUsageReplicas cluster=clusterIdentifier/>
                <@sql.topClusterTopicUnboundedUsageReplicas cluster=clusterIdentifier/>
                <#if hasOrphaned>
                    <br/>
                    <br/>
                    <@sql.orphanReplicasOnCluster cluster=clusterIdentifier/>
                </#if>
            </td>
        </tr>
    </table>
    <br/>
    
    <div class="card">
        <div class="card-header">
            <h4>Disk resource usage</h4>
        </div>
        <div class="card-body p-0">
            <#include "resourcesInspect.ftl">
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
