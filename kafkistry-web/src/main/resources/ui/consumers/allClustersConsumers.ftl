<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer groups</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/consumerGroups.js?ver=${lastCommit}"></script>
</head>

<body>

<style>
    .tooltip-inner {
        max-width: 750px !important;
    }
</style>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <#assign statusId = "allConsumerGroups">
    <#include "../common/serverOpStatus.ftl">
    <#assign statusId = "">
    <div id="all-consumer-groups-result"></div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
