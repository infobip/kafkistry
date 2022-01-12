<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="entityInspection"  type="com.infobip.kafkistry.service.quotas.EntityQuotasInspection" -->
<#-- @ftlvariable name="pendingEntityQuotasRequests"  type="java.util.List<com.infobip.kafkistry.service.AclsRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Quotas entity</title>
    <meta name="current-nav" content="nav-quotas"/>
    <script src="static/git/entityHistory.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "util.ftl" as quotaUtil>

<div class="container">

    <h3>
        <#include "../common/backBtn.ftl">
        Client entity quotas over clusters
    </h3>

    <#assign entityID = entityInspection.entity.asID()>
    <#assign existInRegistry = entityInspection.quotaDescription??>
    <table class="table mb-0">
        <tr>
            <th>Quotas entity</th>
            <td><@quotaUtil.entity entity = entityInspection.entity/></td>
        </tr>
        <tr>
            <th>Entity in registry</th>
            <td><@util.inRegistry flag = existInRegistry/></td>
        </tr>
        <tr>
            <th>OK</th>
            <td><@util.ok ok = entityInspection.status.ok/></td>
        </tr>
        <tr>
            <th>Owner</th>
            <td>
                <#if existInRegistry>
                    ${entityInspection.quotaDescription.owner}
                <#else>
                    <span class="text-primary text-monospace small">[none]</span>
                </#if>
            </td>
        </tr>
        <#if existInRegistry>
            <tr>
                <th>Presence</th>
                <td><@util.presence presence = entityInspection.quotaDescription.presence/></td>
            </tr>
        </#if>
        <tr>
            <th>Actions</th>
            <td>
                <#if existInRegistry>
                    <a href="${appUrl.quotas().showDeleteEntity(entityID)}" class="btn btn-outline-danger">
                        Delete from registry...
                    </a>
                    <a href="${appUrl.quotas().showEditEntity(entityID)}" class="btn btn-outline-primary">
                        Edit entity quotas...
                    </a>
                </#if>
                <#assign entityActions = util.enumListToStringList(entityInspection.availableOperations)>
                <#if !existInRegistry && entityActions?seq_contains("IMPORT_CLIENT_QUOTAS")>
                    <a href="${appUrl.quotas().showImportEntity(entityID)}" class="btn btn-outline-primary">
                        Import to registry...
                    </a>
                </#if>
                <#if existInRegistry && entityActions?seq_contains("EDIT_CLIENT_QUOTAS")>
                    <a href="${appUrl.quotas().showSuggestedEditEntity(entityID)}" class="btn btn-outline-primary">
                        Suggest edit...
                    </a>
                </#if>
                <#if existInRegistry && entityActions?seq_contains("CREATE_MISSING_QUOTAS")>
                    <a href="${appUrl.quotas().showBulkCreateEntityQuotas(entityID)}" class="btn btn-outline-info">
                        Create quotas where missing...
                    </a>
                </#if>
                <#if existInRegistry && entityActions?seq_contains("ALTER_WRONG_QUOTAS")>
                    <a href="${appUrl.quotas().showBulkUpdateEntityQuotas(entityID)}" class="btn btn-outline-warning">
                        Alter all wrong quotas...
                    </a>
                </#if>
                <#if entityActions?seq_contains("DELETE_UNWANTED_QUOTAS")>
                    <a href="${appUrl.quotas().showBulkDeleteEntityQuotas(entityID)}" class="btn btn-outline-danger">
                        Delete all unwanted quotas...
                    </a>
                </#if>
            </td>
        </tr>
        <#if gitStorageEnabled>
            <tr>
                <th>Pending changes</th>
                <td>
                    <#assign pendingRequests = pendingEntityQuotasRequests>
                    <#include "../common/pendingChanges.ftl">
                </td>
            </tr>
        </#if>
    </table>
    <hr/>

    <div class="card">
        <div class="card-header h4">Status per cluster</div>
        <div class="card-body p-0">
            <table class="table m-0">
                <thead class="thead-dark">
                <tr>
                    <th>Cluster</th>
                    <th>Status</th>
                    <th>Producer rate</th>
                    <th>Consumer rate</th>
                    <th>Request percentage</th>
                </tr>
                </thead>
                <#macro quotaValues inspection name suffix>
                    <#assign expected>
                        <@quotaUtil.quotaValue inspection=inspection source="expectedQuota" name=name suffix=suffix/>
                    </#assign>
                    <#assign actual>
                        <@quotaUtil.quotaValue inspection=inspection source="actualQuota" name=name suffix=suffix/>
                    </#assign>
                    <#if expected?markup_string == actual?markup_string>
                        ${expected}
                    <#else>
                        <strong>Expected</strong>: ${expected}
                        <br/>
                        <strong>Actual</strong>: ${actual}
                    </#if>
                </#macro>

                <#list entityInspection.clusterInspections as inspection>
                    <tr>
                        <td>
                            <a href="${appUrl.clusters().showCluster(inspection.clusterIdentifier)}">${inspection.clusterIdentifier}</a>
                        </td>
                        <td>
                            <@util.statusAlert type = inspection.statusType/>
                        </td>
                        <#assign hasWrongValue = inspection.statusType.name() == "WRONG_VALUE">
                        <#assign producerByteRateMismatched = hasWrongValue && !inspection.valuesInspection.producerByteRateOk>
                        <#assign consumerByteRateMismatched = hasWrongValue && !inspection.valuesInspection.consumerByteRateOk>
                        <#assign requestPercentageMismatched = hasWrongValue && !inspection.valuesInspection.requestPercentageOk>
                        <td class="<#if producerByteRateMismatched>value-mismatch</#if>">
                            <@quotaValues inspection=inspection name="producerByteRate" suffix=""/>
                        </td>
                        <td class="<#if consumerByteRateMismatched>value-mismatch</#if>">
                            <@quotaValues inspection=inspection name="consumerByteRate" suffix=""/>
                        </td>
                        <td class="<#if requestPercentageMismatched>value-mismatch</#if>">
                            <@quotaValues inspection=inspection name="requestPercentage" suffix="%"/>
                        </td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>

    <br/>
    <div class="card">
        <div class="card-header h4">Affected principals</div>
        <div class="card-body p-0">
            <#if entityInspection.affectedPrincipals?size == 0>
                <div class="p-2">
                    <i>(none)</i>
                </div>
            <#else>
                <table class="table table-sm">
                    <thead class="thead-dark">
                    <tr>
                        <th>Principal</th>
                        <th>Affected on clusters</th>
                    </tr>
                    </thead>
                    <#list entityInspection.affectedPrincipals as principal, presence>
                        <tr>
                            <td>
                                <a class="btn btn-sm btn-outline-dark"
                                   href="${appUrl.acls().showAllPrincipalAcls(principal)}">
                                    ${principal}</a>
                            </td>
                            <td><@util.presence presence=presence/></td>
                        </tr>
                    </#list>
                </table>
            </#if>
        </div>
    </div>

    <#if existInRegistry>
        <#if gitStorageEnabled>
            <br/>
            <#assign historyUrl = appUrl.quotas().showEntityHistory(entityID)>
            <#include "../git/entityHistoryContainer.ftl">
        </#if>
    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>