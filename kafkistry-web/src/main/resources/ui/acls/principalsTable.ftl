<#-- @ftlvariable name="principals"  type="java.util.List<com.infobip.kafkistry.service.acl.PrincipalAclsClustersPerRuleInspection>" -->
<#-- @ftlvariable name="principalsOwned" type="java.util.Map<java.lang.String, java.lang.Boolean>" -->

<#import "../common/util.ftl" as util>
<#import "util.ftl" as aclUtil>

<#assign datatableId = "principals">
<#include "../common/loading.ftl">
<table id="${datatableId}" class="table table-hover table-bordered dataTable" style="display: none;">
    <thead class="table-theme-dark">
    <tr>
        <th>Principal</th>
        <th>Owner</th>
        <th>OK</th>
        <th>#Rules</th>
        <th>Statuses</th>
    </tr>
    </thead>
    <tbody>
        <#list principals as principalStatus>
            <#assign principalUrl = "acls/principal?principal=${principalStatus.principal?url}">
            <#assign principalOwned = principalsOwned[principalStatus.principal]>
            <tr>
                <td data-order="${principalOwned?then("0", "1")}_${principalStatus.principal}">
                    <a href="${appUrl.acls().showAllPrincipalAcls(principalStatus.principal)}"
                       title="Inspect this principal...">
                        ${principalStatus.principal}
                    </a>
                </td>
                <#if principalStatus.principalAcls??>
                    <td data-order="${principalOwned?then("0", "1")}_${principalStatus.principalAcls.owner}">
                        ${principalStatus.principalAcls.owner}
                        <#if principalOwned>
                            <@util.yourOwned what="principal"/>
                        </#if>
                    </td>
                <#else>
                    <td data-order="00_[none]">
                        <span class="text-primary font-monospace small">[none]</span>
                    </td>
                </#if>
                <td><@util.ok ok = principalStatus.status.ok/></td>
                <td>
                    <#if principalStatus.principalAcls??>
                        ${principalStatus.principalAcls.rules?size}
                    <#else>
                        ${principalStatus.statuses?size}
                    </#if>
                </td>
                <td>
                    <#list principalStatus.status.statusCounts as statusCount>
                        <@util.namedTypeStatusAlert type = statusCount.type quantity = statusCount.quantity/>
                    </#list>
                </td>
            </tr>
        </#list>
    </tbody>
</table>
