<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
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
            <#assign statusId = "allPrincipals">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
            <div id="all-principals-result"></div>
        </div>

    </div>
</div>


<#include "../common/pageBottom.ftl">
</body>
</html>