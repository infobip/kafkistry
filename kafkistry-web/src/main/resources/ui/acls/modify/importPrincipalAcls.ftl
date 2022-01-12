
<#-- @ftlvariable name="principalAcls" type="com.infobip.kafkistry.model.PrincipalAclRules" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/principalAclsForm.js"></script>
    <script src="static/acls-js/createPrincipal.js"></script>
    <script src="static/presenceForm.js"></script>
    <title>Kafkistry: Import new principal ACLs</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">

    <h1>Import principal ACLs</h1>

    <hr>

    <#include "../form/principalAclsForm.ftl">

    <br/>

    <button id="create-btn" class="btn btn-primary btn-sm">Import</button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
    <br/>

    <#include "../../common/entityYaml.ftl">

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>