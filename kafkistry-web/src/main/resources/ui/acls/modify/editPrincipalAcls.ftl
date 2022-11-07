<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="principalAcls" type="com.infobip.kafkistry.model.PrincipalAclRules" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="title" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/principalAclsForm.js?ver=${lastCommit}"></script>
    <script src="static/acls-js/editPrincipal.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Edit principal ACLs</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">

    <h1>${title}</h1>

    <hr>

    <#include "../form/principalAclsForm.ftl">

    <br/>

    <button id="save-btn" class="btn btn-primary btn-sm">Save</button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
    <br/>

    <#include "../../common/entityYaml.ftl">

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>