<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="entityQuotas" type="com.infobip.kafkistry.model.QuotaDescription" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/quotas-js/modifyEntityQuotas.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Delete entity quota</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as util>

<div class="container">

    <h1>Delete entity quotas from registry</h1>
    <hr>

    <p>
        <span class="h4">Entity: </span> <@util.entity entity=entityQuotas.entity/>
    </p>

    <br/>
    <#include "../../common/updateForm.ftl">
    <br/>

    <button id="delete-btn" data-entity-id="${entityQuotas.entity.asID()?html}" class="btn btn-danger btn-sm">
        Delete entity quotas
    </button>
    <#include "../../common/cancelBtn.ftl">
    <#include "../../common/createPullRequestReminder.ftl">

    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>