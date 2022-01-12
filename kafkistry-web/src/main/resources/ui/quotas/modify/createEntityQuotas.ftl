
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "modifyResources.ftl">
    <title>Kafkistry: Create new entity quotas</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <#assign newName = "">

    <h1>
        <#include "../../common/backBtn.ftl">
        Create entity quotas
    </h1>

    <hr>

    <#include "../form/entityQuotasForm.ftl">

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