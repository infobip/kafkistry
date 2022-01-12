
<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="entityQuotaRequest" type="com.infobip.kafkistry.service.quotas.QuotasRequest" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "modifyResources.ftl">
    <title>Kafkistry: Edit principal ACLs</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <h2>${title}</h2>

    <p>Branch name: <strong>${branch}</strong></p>
    <hr>

    <#if entityQuotaRequest.errorMsg??>
        <div role="alert" class="alert alert-danger" title="${entityQuotaRequest.errorMsg}">
            Corrupted content!
        </div>
        <p><strong>Cause:</strong> <span style="color: red;">${entityQuotaRequest.errorMsg}</span></p>
        <p>Please fix corruption in storage files</p>
    </#if>

    <#if entityQuotaRequest.quota??>
        <#assign entityQuotas = entityQuotaRequest.quota>
        <#include "../form/entityQuotasForm.ftl">

        <br/>

        <#switch entityQuotaRequest.type.name()>
            <#case "ADD">
                <button id="create-btn" class="btn btn-primary btn-sm">Edit pending import to registry</button>
                <#break>
            <#case "UPDATE">
                <button class="btn btn-primary btn-sm" id="edit-btn">Edit pending edit request</button>
                <#break>
            <#case "DELETE">
                <div role="alert" class="alert alert-danger">
                    Can't edit DELETE operation
                </div>
                <#break>
            <#default>
                <strong>Cant perform change</strong>
                <#break>
        </#switch>
    </#if>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#if entityQuotaRequest.quota??>
        <#include "../../common/serverOpStatus.ftl">
        <br/>

        <#include "../../common/entityYaml.ftl">
    </#if>

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>