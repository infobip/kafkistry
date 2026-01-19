<#-- @ftlvariable name="clusterQuotas"  type="com.infobip.kafkistry.service.quotas.ClusterQuotasInspection" -->

<#import "../common/util.ftl" as util>

<table class="table table-hover table-sm m-0">
    <#list clusterQuotas.status.statusCounts as statusTypeCount>
        <#assign statusType = statusTypeCount.type>
        <#assign count = statusTypeCount.quantity>
        <tr>
            <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType.name}"
                title="Click to filter by..." data-table-id="quotas">
                <#assign stateClass = util.levelToHtmlClass(statusType.level)>
                <div class="alert alert-sm ${stateClass} mb-0">
                    ${statusType.name}
                </div>
            </td>
            <td style="text-align: right;">${count}</td>
        </tr>
    </#list>
</table>
