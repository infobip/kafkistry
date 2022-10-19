<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="affectingAcls" type="java.util.List<com.infobip.kafkistry.kafka.KafkaAclRule>" -->

<#import "util.ftl" as aclUtil>

<#macro aclRuleRow aclRule>
<#-- @ftlvariable name="aclRule" type="com.infobip.kafkistry.kafka.KafkaAclRule" -->
    <td>
        <a href="${appUrl.acls().showAllPrincipalAclsRule(aclRule.principal, aclRule.toString())}">
            <button class="btn btn-sm btn-outline-dark mb-1" title="Inspect this principal's rule...">
                ${aclRule.principal} 🔍
            </button>
        </a>
    </td>
    <td>${aclRule.host}</td>
    <td><@aclUtil.resource resource = aclRule.resource/></td>
    <td><@aclUtil.operation type = aclRule.operation.type/></td>
    <td><@aclUtil.policy policy = aclRule.operation.policy/></td>
</#macro>

<div class="card">
    <div class="card-header">
        <span class="h4">Affected by ACL rules (${affectingAcls?size})</span>
    </div>
    <div class="card-body p-0">
        <#if affectingAcls?size == 0>
            <i class="m-2">(none)</i>
        <#else>
            <table class="table table-sm">
                <thead class="thead-dark">
                <tr>
                    <th>Principal</th>
                    <th>Host</th>
                    <th>Resource</th>
                    <th>Operation</th>
                    <th>Policy</th>
                </tr>
                </thead>
                <tbody>
                <#assign wildcardAcls = []>
                <#assign acls = []>
                <#list affectingAcls as aclRule>
                    <#if aclRule.resource.name == '*'>
                        <#assign wildcardAcls = wildcardAcls + [aclRule]>
                    <#else>
                        <#assign acls = acls + [aclRule]>
                    </#if>
                </#list>
                <#list acls as aclRule>
                    <tr><@aclRuleRow aclRule=aclRule/></tr>
                </#list>
                <#if wildcardAcls?size gt 0>
                    <tr>
                        <td colspan="100" class="collapsed" data-target=".wildcard-acl-rule" data-toggle="collapsing">
                            <div class="p-2">
                                <span class="when-collapsed" title="expand...">▼</span>
                                <span class="when-not-collapsed" title="collapse...">△</span>
                                <span class="message">${wildcardAcls?size} rule(s) wildcard for ALL resources...</span>
                            </div>
                        </td>
                    </tr>
                    <#list wildcardAcls as aclRule>
                        <tr class="wildcard-acl-rule collapseable">
                            <@aclRuleRow aclRule=aclRule/>
                        </tr>
                    </#list>
                </#if>
                </tbody>
            </table>
        </#if>
    </div>
</div>