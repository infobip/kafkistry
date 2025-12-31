<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

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

    <#assign activeNavItem = "">
    <#include "submenu.ftl">
    <h2>Kafkistry: About</h2>
    <hr/>

    <ul>
        <li><a href="${appUrl.about().showBuildInfo()}">Build info</a></li>
        <li><a href="${appUrl.about().showUsersSessions()}">Users sessions</a></li>
        <li><a href="${appUrl.about().showScrapingStatuses()}">Scraping statuses</a></li>
        <li><a href="${appUrl.about().showBackgroundJobs()}">Background jobs</a></li>
        <li><a href="${appUrl.about().showEnvironment()}">Environment & Properties</a></li>
    </ul>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>