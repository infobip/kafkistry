<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="entity" type="com.infobip.kafkistry.model.QuotaEntity" -->
<#-- @ftlvariable name="missingClusterQuotas" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.quotas.QuotasInspection>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/quotas-js/bulkManagement.js?ver=${lastCommit}"></script>
    <script src="static/bulkUtil.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create missing entity quotas</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as quotaUtil>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Bulk create missing entity quotas on clusters</h2>
    <hr/>

    <h4>Going to create Entity quotas on clusters</h4>
    <br/>
    <p>
        <strong>Entity:</strong>
        <a class=""
           href="${appUrl.quotas().showEntity(entity.asID())}">
            <@quotaUtil.entity entity = entity/>
        </a>
    </p>
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
        <#list missingClusterQuotas as clusterIdentifier, inspection>
            <tr class="missing-quotas-cluster" data-cluster-identifier="${clusterIdentifier}">
                <td>
                    <a class=""
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
                    <#assign statusId = "op-status-"+clusterIdentifier>
                    <#include "../../common/serverOpStatus.ftl">
                </td>
            </tr>
        </#list>
    </table>
    <br/>

    <#if missingClusterQuotas?size gt 0>
        <button id="bulk-create-entity-quotas-btn" class="btn btn-primary btn-sm" data-entity-id="${entity.asID()}">
            Create missing quotas on all clusters (${missingClusterQuotas?size})
        </button>
    <#else>
        <p><i>No clusters need creation of entity quotas</i></p>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#assign statusId = "">
    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>