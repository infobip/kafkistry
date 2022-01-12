
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "modifyResources.ftl">
    <title>Kafkistry: Import entity quotas</title>
</head>

<body>

<#include "../../commonMenu.ftl">

<div class="container">
    <#assign newName = "">

    <h1>
        <#include "../../common/backBtn.ftl">
        Import entity quotas to registry
    </h1>

    <hr>

    <#include "../form/entityQuotasForm.ftl">

    <br/>

    <button id="import-btn" class="btn btn-primary btn-sm">Import</button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
    <br/>

    <#include "../../common/entityYaml.ftl">

</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>