<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="quotasStats"  type="java.util.Map<com.infobip.kafkistry.service.quotas.QuotasInspectionResultType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if quotasStats?size == 0>
    <div class="p-3">
        <a class="btn btn-sm btn-primary" href="${appUrl.quotas().showCreateEntity()}">
            Add entity quotas...
        </a>
    </div>
<#else>
    <table class="table table-hover table-sm m-0">
        <#list quotasStats as stateType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm text-start"
                       href="${appUrl.quotas().showAll()}#${stateType.name}" title="Click to filter quotas...">
                        <@util.namedTypeStatusAlert type = stateType alertInline=false/>
                    </a>
                </td>
                <td class="text-end align-content-center pe-2">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
