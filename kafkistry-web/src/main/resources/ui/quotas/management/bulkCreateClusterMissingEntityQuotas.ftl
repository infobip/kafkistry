<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="missingEntityQuotas" type="java.util.Map<com.infobip.kafkistry.model.QuotaEntity, com.infobip.kafkistry.service.quotas.QuotasInspection>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/quotas-js/bulkManagement.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create cluster missing entity quotas</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as quotaUtil>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Bulk create all missing entity quotas on cluster</h2>
    <hr/>

    <h4>Going to create Entity quotas on cluster</h4>
    <br/>
    <p><strong>Cluster:</strong> <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a></p>
    <hr/>

    <table class="table table-hover table-bordered">
        <thead class="table-theme-dark">
        <tr>
            <th>Entity</th>
            <th>Cluster</th>
            <th>Producer byte rate</th>
            <th>Consumer byte rate</th>
            <th>Request percentage</th>
        </tr>
        </thead>
        <#list missingEntityQuotas as entity, inspection>
            <tr class="missing-quotas-entity thead-light" data-entity-id="${entity.asID()}">
                <td>
                    <a title="Inspect this entity..."
                       href="${appUrl.quotas().showEntity(entity.asID())}">
                        <@quotaUtil.entity entity = entity/>
                    </a>
                </td>
                <td>
                    <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
                </td>
                <td>
                    <@quotaUtil.quotaValue inspection=inspection source="expectedQuota" name="producerByteRate" suffix=""/>
                </td>
                <td>
                    <@quotaUtil.quotaValue inspection=inspection source="expectedQuota" name="consumerByteRate" suffix=""/>
                </td>
                <td>
                    <@quotaUtil.quotaValue inspection=inspection source="expectedQuota" name="requestPercentage" suffix="%"/>
                </td>
            </tr>
            <tr>
                <td colspan="100">
                    <#assign statusId = "op-status-"+entity.asID()?replace("[:<>|;]", "_", "r")>
                    <#include "../../common/serverOpStatus.ftl">
                </td>
            </tr>
        </#list>
    </table>
    <br/>

    <#if missingEntityQuotas?size gt 0>
        <button id="bulk-create-cluster-entity-quotas-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${clusterIdentifier}">
            Create missing quotas for all entities (${missingEntityQuotas?size})
        </button>
    <#else>
        <p><i>No entities need creation of missing quotas</i></p>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#assign statusId = "">
    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>