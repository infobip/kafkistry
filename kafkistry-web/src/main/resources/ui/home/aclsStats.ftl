<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="aclsStats"  type="java.util.Map<com.infobip.kafkistry.service.acl.AclInspectionResultType, java.lang.Integer>" -->
<#-- @ftlvariable name="ownedAclsStats"  type="java.util.Map<com.infobip.kafkistry.service.acl.AclInspectionResultType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if aclsStats?size == 0>
    <div class="all-acls-stats owned-acls-stats">
        <div class="p-3">
            <a class="btn btn-sm btn-primary" href="${appUrl.acls().showCreatePrincipal()}">
                Add ACL(s)...
            </a>
        </div>
    </div>
<#else>
    <#macro aclsStatsTable aStats navigateSuffix="">
        <#-- @ftlvariable name="aStats"  type="java.util.Map<com.infobip.kafkistry.service.acl.AclInspectionResultType, java.lang.Integer>" -->
        <table class="table table-sm m-0">
            <#list aStats as stateType, count>
                <tr>
                    <td>
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${appUrl.acls().showAll()}#${stateType.name}${navigateSuffix}" title="Click to filter ACLs...">
                            <@util.namedTypeStatusAlert type = stateType alertInline=false/>
                        </a>
                    </td>
                    <td class="text-right">${count}</td>
                </tr>
            </#list>
        </table>
    </#macro>
    <div class="all-acls-stats">
        <@aclsStatsTable aStats=aclsStats/>
    </div>
    <div class="owned-acls-stats">
        <#if ownedAclsStats?size == 0>
            <div class="p-2">
                <i>(no ACLs in your ownership)</i>
            </div>
        <#else>
            <@aclsStatsTable aStats=ownedAclsStats navigateSuffix="%20YOUR"/>
        </#if>
    </div>
</#if>
