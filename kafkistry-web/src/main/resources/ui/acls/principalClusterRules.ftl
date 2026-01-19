<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="principalClusterRules"  type="com.infobip.kafkistry.service.acl.PrincipalAclsInspection" -->
<#-- @ftlvariable name="selectedCluster"  type="java.lang.String" -->

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as aclUtil>

<table class="table table-hover table-bordered mb-0 rules-table">
    <thead class="table-theme-dark">
    <tr>
        <th class="toggle-column" data-bs-toggle="collapse" data-bs-target=".cluster-info-row"></th>
        <th colspan="3">Cluster</th>
        <th>OK</th>
        <th>Statuses</th>
        <th>Action</th>
    </tr>
    </thead>
    <tbody>
    <#assign selectedCluster = (selectedCluster??)?then(selectedCluster, "")>

    <#list principalClusterRules.clusterInspections as clusterStatuses>
        <#assign clusterIdentifier = clusterStatuses.clusterIdentifier>
        <#assign shown = clusterIdentifier == selectedCluster>
        <#assign collapsedClass = shown?then("", "collapsed")>
        <#assign showClass = shown?then("show", "")>
        <tr class="card-header ${collapsedClass} rule-row" data-bs-target=".cluster-${clusterStatuses?index}"
            data-toggle="collapsing">
            <td class="toggle-column p-0" style="height: 1px;">
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
            </td>
            <td colspan="3"><a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a></td>
            <td><@util.ok ok = clusterStatuses.status.ok/></td>
            <td>
                <#list clusterStatuses.status.statusCounts as statusCount>
                    <@util.namedTypeStatusAlert type = statusCount.type quantity = statusCount.quantity/>
                </#list>
            </td>
            <td>
                <#if clusterStatuses.availableOperations?size == 0>
                    ----
                </#if>
                <#list clusterStatuses.availableOperations as operation>
                    <@aclUtil.availableOperation
                    operation=operation
                    principal=principalClusterRules.principal
                    cluster=clusterIdentifier
                    rule=""
                    />
                </#list>
            </td>
        </tr>
        <#assign clusterRowClasses = "cluster-${clusterStatuses?index} card-body p-0 collapseable ${showClass} cluster-info-row table-sm">
        <tr class="${clusterRowClasses} thead-light">
            <th colspan="2">Host <@info.icon tooltip=doc.aclHostHelpMsg/></th>
            <th>Resource</th>
            <th>Operation</th>
            <th>Policy</th>
            <th>Status</th>
            <th>Action</th>
        </tr>
        <#if clusterStatuses.statuses?size == 0>
            <tr class="${clusterRowClasses}">
                <td colspan="8"><i>(no rules on cluster)</i></td>
            </tr>
        </#if>
        <#list clusterStatuses.statuses as ruleStatus>
            <tr class="${clusterRowClasses}">
                <#assign rule = ruleStatus.rule>
                <td colspan="2">${rule.host}</td>
                <td><@aclUtil.resource resource = rule.resource/></td>
                <td><@aclUtil.operation type = rule.operation.type/></td>
                <td><@aclUtil.policy policy = rule.operation.policy/></td>
                <td>
                    <#list ruleStatus.statusTypes as statusType>
                        <@util.namedTypeStatusAlert type = statusType/>
                    </#list>
                <td>
                    <#if ruleStatus.availableOperations?size == 0>
                        ----
                    </#if>
                    <#list ruleStatus.availableOperations as operation>
                        <@aclUtil.availableOperation
                        operation=operation
                        principal=principalClusterRules.principal
                        cluster=clusterIdentifier
                        rule=ruleStatus.rule.toString()
                        />
                    </#list>
                </td>
            </tr>
        </#list>
    </#list>
    </tbody>
</table>
