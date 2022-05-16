<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterQuotas"  type="com.infobip.kafkistry.service.quotas.ClusterQuotasInspection" -->

<#import "../common/util.ftl" as util>
<#import "../quotas/util.ftl" as quotaUtil>

<#assign datatableId = "quotas-table">
<#include "../common/loading.ftl">

<table id="${datatableId}" class="table table-bordered datatable" style="display: none;">
    <thead class="thead-dark">
    <tr>
        <th>Entity</th>
        <th>Producer byte rate</th>
        <th>Consumer byte rate</th>
        <th>Request percentage</th>
        <th>Status</th>
        <th>Action</th>
    </tr>
    </thead>
    <#list clusterQuotas.entityInspections as inspection>
        <#assign entity = inspection.entity>
        <tr class="missing-quotas-entity thead-light" data-entity-id="${entity.asID()}">
            <td>
                <a class="btn btn-sm btn-outline-dark mb-1"
                   href="${appUrl.quotas().showEntity(entity.asID())}">
                    <@quotaUtil.entity entity = entity/>
                </a>
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
            <td>
                <@util.statusAlert type = inspection.statusType/>
            </td>
            <td>

            </td>
        </tr>
    </#list>
</table>
