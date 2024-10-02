<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="quotaEntities"  type="java.util.List<com.infobip.kafkistry.service.quotas.EntityQuotasInspection>" -->
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
            <div class="btn-group float-right">
                <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown"
                        aria-haspopup="true" aria-expanded="false">
                    Create entity quotas...
                </button>
                <div class="dropdown-menu open">
                    <a class="dropdown-item" href="${appUrl.quotas().showCreateEntity()}">New entity quotas</a>
                </div>
            </div>
        </div>

        <div class="card-body pl-0 pr-0">

            <table class="table table-bordered dataTable">
                <thead class="thead-dark">
                <tr>
                    <th>Client Entity</th>
                    <th>Owner</th>
                    <th>Presence</th>
                    <th>OK</th>
                    <th>Statuses</th>
                </tr>
                </thead>
                <tbody>
                <#list quotaEntities as entityStatus>
                    <tr>
                        <td>
                            <a class="btn btn-sm btn-outline-dark mb-1"
                               href="${appUrl.quotas().showEntity(entityStatus.entity.asID())}">
                                <@quotaUtil.entity entity = entityStatus.entity/>
                            </a>
                        </td>
                        <#if entityStatus.quotaDescription??>
                            <td>${entityStatus.quotaDescription.owner}</td>
                            <td><@util.presence presence = entityStatus.quotaDescription.presence inline = false/></td>
                        <#else>
                            <td><span class="text-primary text-monospace small">[none]</span></td>
                            <td><span class="text-primary text-monospace small">[undefined]</span></td>
                        </#if>
                        <td>
                            <@util.ok ok = entityStatus.status.ok/>
                        </td>
                        <td>
                            <#list entityStatus.status.statusCounts as statusCount>
                                <@util.namedTypeStatusAlert type = statusCount.type quantity = statusCount.quantity/>
                            </#list>
                        </td>
                    </tr>
                </#list>
                </tbody>
            </table>
        </div>

    </div>
</div>


<#include "../common/pageBottom.ftl">
</body>
</html>