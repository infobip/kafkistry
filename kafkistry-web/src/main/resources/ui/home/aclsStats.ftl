<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="aclsStats"  type="java.util.Map<com.infobip.kafkistry.service.acl.AclInspectionResultType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if aclsStats?size == 0>
    <div class="p-3">
        <a class="btn btn-sm btn-primary" href="${appUrl.acls().showCreatePrincipal()}">
            Add ACL(s)...
        </a>
    </div>
<#else>
    <table class="table table-sm m-0">
        <#list aclsStats as stateType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.acls().showAll()}#${stateType.name}" title="Click to filter ACLs...">
                        <@util.namedTypeStatusAlert type = stateType alertInline=false/>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
