<#-- @ftlvariable name="clusterQuotas"  type="com.infobip.kafkistry.service.quotas.ClusterQuotasInspection" -->

<#import "../common/util.ftl" as util>

<table class="table table-sm m-0">
    <#list clusterQuotas.status.statusCounts as statusType, count>
        <tr>
            <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType}"
                title="Click to filter by..." data-table-id="quotas">
                <#assign stateClass = util.statusTypeAlertClass(statusType)>
                <div class="alert alert-sm ${stateClass} mb-0">
                    ${statusType}
                </div>
            </td>
            <td style="text-align: right;">${count}</td>
        </tr>
    </#list>
</table>
