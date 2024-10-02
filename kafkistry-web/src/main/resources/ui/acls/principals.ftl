<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="principals"  type="java.util.List<com.infobip.kafkistry.service.acl.PrincipalAclsClustersPerRuleInspection>" -->
<#-- @ftlvariable name="pendingPrincipalRequests"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.AclsRequest>>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: ACLs</title>
    <script src="static/acls-js/principals.js?ver=${lastCommit}"></script>
    <meta name="current-nav" content="nav-acls"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "util.ftl" as aclUtil>

<div class="container">
    <#if gitStorageEnabled>
        <#assign pendingUpdates = pendingPrincipalRequests>
        <#assign entityName = "Principal ACLs">
        <#include "../common/pendingChangeRequests.ftl">
        <br/>
    </#if>

    <div class="card">

        <div class="card-header">
            <span class="h4">Status of principals ACLs</span>
            <div class="btn-group float-right">
                <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown"
                        aria-haspopup="true" aria-expanded="false">
                    Create new acl rules...
                </button>
                <div class="dropdown-menu open">
                    <a class="dropdown-item" href="${appUrl.acls().showCreatePrincipal()}">New principal rules</a>
                </div>
            </div>
        </div>

        <div class="card-body pl-0 pr-0">

            <table class="table table-bordered dataTable">
                <thead class="thead-dark">
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
                        <tr>
                            <td>
                                <a href="${appUrl.acls().showAllPrincipalAcls(principalStatus.principal)}"
                                   class="btn btn-sm btn-outline-dark mb-1"
                                   title="Inspect this principal...">
                                    ${principalStatus.principal}
                                </a>
                            </td>
                            <td>
                                <#if principalStatus.principalAcls??>
                                    ${principalStatus.principalAcls.owner}
                                <#else>
                                    <span class="text-primary text-monospace small">[none]</span>
                                </#if>
                            </td>
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
        </div>

    </div>
</div>


<#include "../common/pageBottom.ftl">
</body>
</html>