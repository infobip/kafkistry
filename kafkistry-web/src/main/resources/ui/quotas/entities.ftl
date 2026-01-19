<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="pendingQuotaRequests"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.quotas.QuotasRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Quotas</title>
    <script src="static/quotas-js/quotas.js?ver=${lastCommit}"></script>
    <meta name="current-nav" content="nav-quotas"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "util.ftl" as quotaUtil>

<div class="container">
    <#if gitStorageEnabled>
        <#assign pendingUpdates = pendingQuotaRequests>
        <#assign entityName = "Entity quota">
        <#include "../common/pendingChangeRequests.ftl">
        <br/>
    </#if>

    <div class="card">

        <div class="card-header">
            <span class="h4">Status of client entity quotas</span>
            <div class="btn-group float-end">
                <button type="button" class="btn btn-primary dropdown-toggle" data-bs-toggle="dropdown"
                        aria-haspopup="true" aria-expanded="false">
                    Create entity quotas...
                </button>
                <div class="dropdown-menu">
                    <a class="dropdown-item" href="${appUrl.quotas().showCreateEntity()}">New entity quotas</a>
                </div>
            </div>
        </div>

        <div class="card-body pl-0 pr-0">
            <#assign statusId = "allQuotaEntities">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="all-quota-entities-result"></div>
        </div>

    </div>
</div>


<#include "../common/pageBottom.ftl">
</body>
</html>