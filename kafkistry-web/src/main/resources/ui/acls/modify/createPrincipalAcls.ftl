<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/principalAclsForm.js?ver=${lastCommit}"></script>
    <script src="static/acls-js/createPrincipal.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create new principal ACLs</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <#assign newName = "">

    <h1>Create principal ACLs</h1>

    <hr>

    <#include "../form/principalAclsForm.ftl">

    <br/>

    <button id="create-btn" class="btn btn-primary btn-sm">Create</button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
    <br/>

    <#include "../../common/entityYaml.ftl">

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>