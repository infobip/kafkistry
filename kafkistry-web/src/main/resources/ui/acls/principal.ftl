<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="principalRuleClusters"  type="com.infobip.kafkistry.service.PrincipalAclsClustersPerRuleInspection" -->
<#-- @ftlvariable name="principalClusterRules"  type="com.infobip.kafkistry.service.PrincipalAclsInspection" -->
<#-- @ftlvariable name="selectedRule"  type="java.lang.String" -->
<#-- @ftlvariable name="selectedCluster"  type="java.lang.String" -->
<#-- @ftlvariable name="pendingPrincipalRequests"  type="java.util.List<com.infobip.kafkistry.service.AclsRequest>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/acls-js/principalAcls.js?ver=${lastCommit}"></script>
    <script src="static/git/entityHistory.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Principal acls</title>
    <meta name="current-nav" content="nav-acls"/>
    <style>
        .toggle-column {
            width: 30px;
            padding: 0 !important;
            vertical-align: middle !important;
            text-align: center !important;
        }
    </style>
</head>

<body>


<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "util.ftl" as aclUtil>

<#assign principal = principalRuleClusters.principal>

<div class="container">

    <h3>
        <#include "../common/backBtn.ftl">
        Principal ACLs: <span class="text-monospace">${principal}</span>
    </h3>

    <#assign existInRegistry = principalRuleClusters.principalAcls??>
    <table class="table mb-0">
        <tr>
            <th>Principal</th>
            <td><strong>${principal}</strong></td>
        </tr>
        <tr>
            <th>Principal in registry</th>
            <td><@util.inRegistry flag = existInRegistry/></td>
        </tr>
        <tr>
            <th>OK</th>
            <td><@util.ok ok = principalRuleClusters.status.ok/></td>
        </tr>
        <tr>
            <th>Owner</th>
            <td>
                <#if existInRegistry>
                    ${principalRuleClusters.principalAcls.owner}
                <#else>
                    <span class="text-primary text-monospace small">[none]</span>
                </#if>
            </td>
        </tr>
        <#if existInRegistry>
            <tr>
                <th>Description</th>
                <td style="white-space: pre-wrap;" id="description" class="text-links">${principalRuleClusters.principalAcls.description}</td>
            </tr>
        </#if>
        <tr>
            <th>Actions</th>
            <td>
                <#if existInRegistry>
                    <a href="${appUrl.acls().showDeletePrincipal(principal)}" class="btn btn-outline-danger">
                        Delete from registry...
                    </a>
                    <a href="${appUrl.acls().showEditPrincipal(principal)}" class="btn btn-outline-primary">
                        Edit ACLs...
                    </a>
                </#if>
                <#assign principalActions = util.enumListToStringList(principalRuleClusters.availableOperations)>
                <#if !existInRegistry && principalActions?seq_contains("IMPORT_PRINCIPAL")>
                    <a href="${appUrl.acls().showImportPrincipal(principal)}" class="btn btn-outline-primary">
                        Import to registry...
                    </a>
                </#if>
                <#if existInRegistry && principalActions?seq_contains("EDIT_PRINCIPAL_ACLS")>
                    <a href="${appUrl.acls().showSuggestedEditPrincipal(principal)}" class="btn btn-outline-primary">
                        Suggest edit...
                    </a>
                </#if>
                <#if existInRegistry && principalActions?seq_contains("CREATE_MISSING_ACLS")>
                    <a href="${appUrl.acls().showBulkCreatePrincipalRules(principal)}" class="btn btn-outline-info">
                        Create all missing ACLs...
                    </a>
                </#if>
                <#if principalActions?seq_contains("DELETE_UNWANTED_ACLS")>
                    <a href="${appUrl.acls().showBulkDeletePrincipalRules(principal)}" class="btn btn-outline-danger">
                        Delete all unwanted ACLs...
                    </a>
                </#if>
            </td>
        </tr>
        <#if gitStorageEnabled>
            <tr>
                <th>Pending changes</th>
                <td>
                    <#assign pendingRequests = pendingPrincipalRequests>
                    <#include "../common/pendingChanges.ftl">
                </td>
            </tr>
        </#if>
    </table>
    <hr/>

    <#if selectedRule?? || !(selectedCluster??)>
        <#assign ruleClusters = true>
        <#assign clusterRules = false>
    <#else>
        <#assign ruleClusters = false>
        <#assign clusterRules = true>
    </#if>

    <div class="card">
        <div class="card-header">
            <div class="float-left">
                <span class="h4">ACL rule statuses</span>
            </div>
            <#if existInRegistry>
                <div class="float-right">
                    <a href="${appUrl.acls().showEditPrincipal(principal)}" class="btn btn-primary">
                        Add new rule...
                    </a>
                </div>
            </#if>
            <div class="text-center">
                Display as:
                <label class="btn btn-outline-primary <#if ruleClusters>active</#if>">
                    Rules clusters
                    <input type="radio" name="breakdown" value="rule-clusters" <#if ruleClusters>checked</#if>>
                </label>
                <label class="btn btn-outline-primary <#if clusterRules>active</#if>">
                    Clusters rules
                    <input type="radio" name="breakdown" value="cluster-rules" <#if clusterRules>checked</#if>>
                </label>
            </div>
        </div>

        <div class="card-body pl-0 pr-0 pb-0">
            <div id="rule-clusters" <#if !ruleClusters>style="display: none;"</#if>>
                <#include "principalRuleClusters.ftl">
            </div>
            <div id="cluster-rules" <#if !clusterRules>style="display: none;"</#if>>
                <#include "principalClusterRules.ftl">
            </div>
        </div>
    </div>

    <#import "../quotas/util.ftl" as quotaUtil>
    <br/>
    <div class="card">
        <div class="card-header h4">Affected by entity quotas</div>
        <div class="card-body p-0">
            <#if principalClusterRules.affectingQuotaEntities?size == 0>
                <div class="p-2">
                    <i>(none)</i>
                </div>
            <#else>
                <table class="table table-sm">
                    <thead class="thead-dark">
                    <tr>
                        <th>Quota entity</th>
                        <th>Affected by on clusters</th>
                    </tr>
                    </thead>
                    <#list principalClusterRules.affectingQuotaEntities as entity, presence>
                        <tr>
                            <td>
                                <a class="btn btn-sm btn-outline-dark" href="${appUrl.quotas().showEntity(entity.asID())}">
                                    <@quotaUtil.entity entity=entity/>
                                </a>
                            </td>
                            <td><@util.presence presence=presence/></td>
                        </tr>
                    </#list>
                </table>
            </#if>
        </div>
    </div>


    <#if existInRegistry>
        <#if gitStorageEnabled>
            <br/>
            <#assign historyUrl = appUrl.acls().showPrincipalHistory(principal)>
            <#include "../git/entityHistoryContainer.ftl">
        </#if>

        <br/>
        <div class="card">
            <div class="card-header">
                <h4>Principal ACLs in registry</h4>
                <span>File name: <span style="font-family: monospace;" id="principal-filename"></span></span>
            </div>
            <div class="card-body p-1">
                <pre id="principal-yaml" data-principal="${principalRuleClusters.principal}"></pre>
            </div>
        </div>

    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
