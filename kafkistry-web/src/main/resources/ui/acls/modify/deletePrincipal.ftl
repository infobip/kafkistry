<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="principalAcls" type="com.infobip.kafkistry.model.PrincipalAclRules" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/deletePrincipal.js"></script>
    <title>Kafkistry: Delete principal</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <#assign newName = "">

    <h1>Delete principal ACLs from registry</h1>
    <hr>

    <h4>Going to delete principal (${principalAcls.principal}) from registry</h4>

    <br/>
    <label>
        Delete reason message:
        <input class="width-full" id="delete-message" type="text">
    </label>
    <br/>
    <#if gitStorageEnabled>
        <label>
            Choose branch to write into:
            <input class="width-full" name="targetBranch" placeholder="custom branch name or (empty) for default">
        </label>
        <br/>
    </#if>

    <button id="delete-principal-btn" data-principal="${principalAcls.principal}" class="btn btn-danger btn-sm">
        Delete principal
    </button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>