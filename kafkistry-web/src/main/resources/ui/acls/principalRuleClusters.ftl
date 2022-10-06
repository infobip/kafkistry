<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="principalRuleClusters"  type="com.infobip.kafkistry.service.acl.PrincipalAclsClustersPerRuleInspection" -->
<#-- @ftlvariable name="selectedRule"  type="java.lang.String" -->
<#-- @ftlvariable name="hideActions"  type="java.lang.Boolean" -->

<#import "../common/util.ftl" as util>
<#import "util.ftl" as aclUtil>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<table class="table table-bordered mb-0 rules-table">
    <thead class="thead-dark">
    <tr>
        <th class="toggle-column" data-toggle="collapsing" data-target=".rule-info-row"></th>
        <th>Host <@info.icon tooltip=doc.aclHostHelpMsg/></th>
        <th>Resource</th>
        <th>Operation</th>
        <th>Policy</th>
        <th>OK</th>
        <th>Statuses</th>
    </tr>
    </thead>
    <tbody>
    <#assign selectedRule = (selectedRule??)?then(selectedRule, "")>
    <#assign hideActions = (hideActions??)?then(hideActions, false)>

    <#if principalRuleClusters.statuses?size == 0>
        <tr>
            <td colspan="100"><i>(no rules)</i></td>
        </tr>
    </#if>

    <#list principalRuleClusters.statuses as ruleStatuses>
        <#assign shown = (ruleStatuses.aclRule.toString() == selectedRule)>
        <#assign collapsedClass = shown?then("", "collapsed")>
        <#assign showClass = shown?then("show hover", "")>
        <tr class="card-header ${collapsedClass} rule-row" data-target=".rule-${ruleStatuses?index}"
            data-toggle="collapsing">
            <td class="toggle-column p-0" style="height: 1px;">
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
            </td>
            <#assign rule = ruleStatuses.aclRule>
            <td>${rule.host}</td>
            <td><@aclUtil.resource resource = rule.resource/></td>
            <td><@aclUtil.operation type = rule.operation.type/></td>
            <td><@aclUtil.policy policy = rule.operation.policy/></td>
            <td><@util.ok ok = ruleStatuses.status.ok/></td>
            <td>
                <#list ruleStatuses.status.statusCounts as statusCount>
                    <@util.namedTypeStatusAlert type = statusCount.type/>
                </#list>
            </td>
        </tr>
        <#assign ruleRowClasses = "rule-${ruleStatuses?index} card-body p-0 collapseable ${showClass} rule-info-row table-sm">
        <tr class="${ruleRowClasses} thead-light">
            <th colspan="2">On cluster</th>
            <th colspan="${hideActions?then("2", "1")}">Affected resources</th>
            <th colspan="${hideActions?then("2", "1")}">Conflicts</th>
            <#if !hideActions>
                <th colspan="2">Action</th>
            </#if>
            <th>Status</th>
        </tr>
        <#list ruleStatuses.clusterStatuses as clusterIdentifier, clusterStatus>
            <tr class="${ruleRowClasses}">
                <td colspan="2">
                    <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
                </td>
                <td colspan="${hideActions?then("2", "1")}">
                    <#assign ruleStatus = clusterStatus>
                    <#include "affectedResources.ftl">
                </td>
                <td colspan="${hideActions?then("2", "1")}">
                    <#if clusterStatus.conflictingAcls?size == 0>
                        <i>(none)</i>
                    <#else>
                        <#list clusterStatus.conflictingAcls as conflictingAcl>
                            <a href="${appUrl.acls().showAllPrincipalAcls(conflictingAcl.principal, conflictingAcl.toString(), clusterIdentifier)}"
                                class="btn btn-sm btn-outline-dark">
                                ${conflictingAcl.toString()}
                            </a>
                        </#list>
                    </#if>
                </td>
                <#if !hideActions>
                    <td colspan="2">
                        <#if clusterStatus.availableOperations?size == 0>
                            ----
                        </#if>
                        <#list clusterStatus.availableOperations as operation>
                            <@aclUtil.availableOperation
                            operation=operation
                            principal=principalRuleClusters.principal
                            cluster=clusterIdentifier
                            rule=clusterStatus.rule.toString()
                            />
                        </#list>
                    </td>
                </#if>
                <td>
                    <#list clusterStatus.statusTypes as statusType>
                        <@util.namedTypeStatusAlert type = statusType/>
                    </#list>
                </td>
            </tr>
        </#list>
    </#list>
    </tbody>
</table>
