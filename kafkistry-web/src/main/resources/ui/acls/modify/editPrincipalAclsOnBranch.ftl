
<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="aclsRequest" type="com.infobip.kafkistry.service.history.AclsRequest" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/principalAclsForm.js"></script>
    <script src="static/acls-js/createPrincipal.js"></script>
    <script src="static/acls-js/editPrincipal.js"></script>
    <script src="static/presenceForm.js"></script>
    <title>Kafkistry: Edit principal ACLs</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <h2>${title}</h2>

    <p>Branch name: <strong>${branch}</strong></p>
    <hr>

    <#if aclsRequest.errorMsg??>
        <div role="alert" class="alert alert-danger" title="${aclsRequest.errorMsg}">
            Corrupted content!
        </div>
        <p><strong>Cause:</strong> <span style="color: red;">${aclsRequest.errorMsg}</span></p>
        <p>Please fix corruption in storage files</p>
    </#if>

    <#if aclsRequest.principalAcls??>
        <#assign principalAcls = aclsRequest.principalAcls>
        <#include "../form/principalAclsForm.ftl">

        <br/>

        <#switch aclsRequest.type>
            <#case "ADD">
                <button id="create-btn" class="btn btn-primary btn-sm">Edit pending import to registry</button>
                <#break>
            <#case "UPDATE">
                <button class="btn btn-primary btn-sm" id="save-btn">Edit pending edit request</button>
                <#break>
            <#case "DELETE">
                <div role="alert" class="alert alert-danger">
                    Can't edit DELETE operation
                </div>
                <#break>
            <#default>
                <strong>Can't perform change</strong>
                <br/>Having status: ${aclsRequest.type.name()}
                <#break>
        </#switch>
    </#if>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#if aclsRequest.principalAcls??>
        <#include "../../common/serverOpStatus.ftl">
        <br/>

        <#include "../../common/entityYaml.ftl">
    </#if>

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>