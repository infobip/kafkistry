<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="balanceStatus"  type="com.infobip.kafkistry.service.generator.balance.ClusterBalanceStatus" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Cluster balance</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../commonMenu.ftl">
<#import "../sql/sqlQueries.ftl" as sql>

<#import "../common/util.ftl" as util>

<div class="container">
    <h3><#include "../common/backBtn.ftl"> Cluster resource balance</h3>
    <br/>

    <table class="table fixed-layout mt-2">
        <tr>
            <th>Cluster identifier</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
            </td>
        </tr>
        <tr>
            <th>Inspect</th>
            <td>
                <@sql.topClusterTopicReplicas cluster=clusterIdentifier/>
                <@sql.topClusterTopicReplicasPerBroker cluster=clusterIdentifier numBrokers=balanceStatus.brokerIds?size/>
                <br/>
                <br/>
                <@sql.topClusterProducingTopics cluster=clusterIdentifier/>
                <@sql.topClusterProducingTopicsPerBroker cluster=clusterIdentifier numBrokers=balanceStatus.brokerIds?size/>
            </td>
        </tr>
    </table>

    <#assign maxValueClass = "text-primary">
    <#assign minValueClass = "text-info">
    <ul class="list-group list-group-horizontal mb-2">
        <li class="list-group-item p-2">
            <span class="${maxValueClass}">MAX value broker</span>
        </li>
        <li class="list-group-item p-2">
            <span class="${minValueClass}">MIN value broker</span>
        </li>
    </ul>

    <table class="table">
        <thead class="thead-dark">
        <tr>
            <th>Broker/Metric</th>
            <th>Disk</th>
            <th>Rate</th>
            <th>Consume</th>
            <th>Replication</th>
            <th>Replicas</th>
            <th>Leaders</th>
        </tr>
        </thead>
        <#list balanceStatus.brokerLoads as brokerId, load>
            <tr>
                <th>${brokerId?c}</th>

                <#assign maxClass = balanceStatus.maxLoadBrokers.size?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.size?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${util.prettyDataSize(load.size)}</td>

                <#assign maxClass = balanceStatus.maxLoadBrokers.rate?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.rate?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${util.prettyNumber(load.rate)} msg/sec</td>

                <#assign maxClass = balanceStatus.maxLoadBrokers.consumeRate?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.consumeRate?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${util.prettyNumber(load.consumeRate)} msg/sec</td>

                <#assign maxClass = balanceStatus.maxLoadBrokers.replicationRate?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.replicationRate?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${util.prettyNumber(load.replicationRate)} msg/sec</td>

                <#assign maxClass = balanceStatus.maxLoadBrokers.replicas?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.replicas?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${load.replicas}</td>

                <#assign maxClass = balanceStatus.maxLoadBrokers.leaders?seq_contains(brokerId)?then(maxValueClass, "")>
                <#assign minClass = balanceStatus.minLoadBrokers.leaders?seq_contains(brokerId)?then(minValueClass, "")>
                <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                <td class="${classes}">${load.leaders}</td>
            </tr>
        </#list>
        <tr class="thead-light p-0">
            <th colspan="100" class="p-1"></th>
        </tr>
        <tr>
            <th>Average</th>
            <td>${util.prettyDataSize(balanceStatus.brokersAverageLoad.size)}</td>
            <td>${util.prettyNumber(balanceStatus.brokersAverageLoad.rate)} msg/sec</td>
            <td>${util.prettyNumber(balanceStatus.brokersAverageLoad.consumeRate)} msg/sec</td>
            <td>${util.prettyNumber(balanceStatus.brokersAverageLoad.replicationRate)} msg/sec</td>
            <td>${balanceStatus.brokersAverageLoad.replicas}</td>
            <td>${balanceStatus.brokersAverageLoad.leaders}</td>
        </tr>
        <tr>
            <th>Disbalance |max-min|</th>
            <td>${util.prettyDataSize(balanceStatus.brokersLoadDiff.size)}</td>
            <td>${util.prettyNumber(balanceStatus.brokersLoadDiff.rate)} msg/sec</td>
            <td>${util.prettyNumber(balanceStatus.brokersLoadDiff.consumeRate)} msg/sec</td>
            <td>${util.prettyNumber(balanceStatus.brokersLoadDiff.replicationRate)} msg/sec</td>
            <td>${balanceStatus.brokersLoadDiff.replicas}</td>
            <td>${balanceStatus.brokersLoadDiff.leaders}</td>
        </tr>
        <tr>
            <th>Disbalance portion</th>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.size)}%</td>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.rate)}%</td>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.consumeRate)}%</td>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.replicationRate)}%</td>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.replicas)}%</td>
            <td>${util.prettyNumber(balanceStatus.loadDiffPortion.leaders)}%</td>
        </tr>
    </table>
    <br/>

    <a class="btn btn-sm btn-outline-info" href="${appUrl.clusters().showIncrementalBalancing(clusterIdentifier)}">
        Incremental balancing...
    </a>
    <#include "../common/cancelBtn.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
