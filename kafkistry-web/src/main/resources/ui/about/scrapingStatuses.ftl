<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="scrapingStatuses"  type="java.util.List<com.infobip.kafkistry.service.scrapingstatus.ClusterScrapingStatus>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: About</title>
    <meta name="current-nav" content="nav-app-info"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>

<div class="container">

    <#assign activeNavItem = "scraping-statuses">
    <#include "submenu.ftl">

    <table class="table table-bordered datatable">
        <thead class="thead-dark">
        <tr>
            <th>Status type name</th>
            <th>Cluster</th>
            <th>Status</th>
            <th>Refresh by</th>
            <th>Last refresh</th>
        </tr>
        </thead>
        <#list scrapingStatuses as scrapingStatus>
            <tr>
                <td title="Class: ${scrapingStatus.scraperClass}">
                    <code>${scrapingStatus.stateTypeName}</code>
                </td>
                <td>
                    <a href="${appUrl.clusters().showCluster(scrapingStatus.clusterIdentifier)}">
                        ${scrapingStatus.clusterIdentifier}
                    </a>
                </td>
                <td><@util.namedTypeStatusAlert type=scrapingStatus.stateType/></td>
                <td>
                    <code>${scrapingStatus.kafkistryInstance}</code>
                </td>
                <td class="time small" data-time="${scrapingStatus.lastRefreshTime?c}" data-order="${scrapingStatus.lastRefreshTime?c}"></td>
            </tr>
        </#list>
    </table>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>