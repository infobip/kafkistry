<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterAcls"  type="com.infobip.kafkistry.service.acl.ClusterAclsInspection" -->

<#import "../common/util.ftl" as util>
<#import "../acls/util.ftl" as aclUtil>

<#assign datatableId = "acls-table">
<#include "../common/loading.ftl">
<table id="${datatableId}" class="table table-bordered datatable" style="display: none;">
    <thead class="thead-dark">
    <tr>
        <th>Principal</th>
        <th>Host</th>
        <th>Resource</th>
        <th>Operation</th>
        <th>Policy</th>
        <th>Status</th>
        <th>Action</th>
    </tr>
    </thead>
    <#list clusterAcls.principalAclsInspections as principalAclsInspecton>
        <#list principalAclsInspecton.statuses as ruleStatus>
            <#assign rule = ruleStatus.rule>
            <tr>
                <td>
                    <a href="${appUrl.acls().showAllPrincipalAclsCluster(rule.principal, clusterIdentifier)}"
                       class="btn btn-sm btn-outline-dark mb-1"
                       title="Inspect this principal...">
                        ${rule.principal}
                    </a>
                </td>
                <td>${rule.host}</td>
                <td><@aclUtil.resource resource = rule.resource/></td>
                <td><@aclUtil.operation type = rule.operation.type/></td>
                <td><@aclUtil.policy policy = rule.operation.policy/></td>
                <td><@util.statusAlert type = ruleStatus.statusType/></td>
                <td>
                    <#if ruleStatus.availableOperations?size == 0>
                        ----
                    </#if>
                    <#list ruleStatus.availableOperations as operation>
                        <@aclUtil.availableOperation
                        operation=operation
                        principal=rule.principal
                        cluster=clusterIdentifier
                        rule=rule.toString()
                        />
                    </#list>
                </td>
            </tr>
        </#list>
    </#list>
</table>
