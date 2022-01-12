
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="title" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "modifyResources.ftl">
    <title>Kafkistry: Edit entity quotas</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">

    <h1>
        <#include "../../common/backBtn.ftl">
        ${title}
    </h1>

    <hr>

    <#include "../form/entityQuotasForm.ftl">

    <br/>

    <button id="edit-btn" class="btn btn-primary btn-sm">Edit</button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
    <br/>

    <#include "../../common/entityYaml.ftl">

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>