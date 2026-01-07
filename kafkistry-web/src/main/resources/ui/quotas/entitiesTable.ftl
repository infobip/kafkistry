<#-- @ftlvariable name="quotaEntities"  type="java.util.List<com.infobip.kafkistry.service.quotas.EntityQuotasInspection>" -->

<#import "../common/util.ftl" as util>
<#import "util.ftl" as quotaUtil>

<#assign datatableId = "quota-entities">
<#include "../common/loading.ftl">
<table id="${datatableId}" class="table table-bordered dataTable" style="display: none;">
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
