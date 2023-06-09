<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="topicRequest" type="com.infobip.kafkistry.service.history.TopicRequest" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "form/topicFormResources.ftl">
    <script src="static/topic/editTopic.js?ver=${lastCommit}"></script>
    <script src="static/topic/importTopic.js?ver=${lastCommit}"></script>
    <script src="static/topic/createTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: ${title}</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">
    <h1>${title}</h1>

    <p>Branch name: <strong>${branch}</strong></p>
    <hr>

    <#if topicRequest.errorMsg??>
        <div role="alert" class="alert alert-danger" title="${topicRequest.errorMsg}">
            Corrupted content!
        </div>
        <p><strong>Cause:</strong> <span style="color: red;">${topicRequest.errorMsg}</span></p>
        <p>Please fix corruption in storage files</p>
    </#if>

    <#if topicRequest.topic??>
        <#assign topic = topicRequest.topic>
        <#include "form/topicForm.ftl">

        <br/>

        <#switch topicRequest.type>
            <#case "ADD">
                <button id="import-btn" class="btn btn-primary btn-sm">Edit pending import to registry</button>
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
                <strong>Can't perform change</strong>
                <br/>Having change type: ${topicRequest.type}
                <#break>
        </#switch>
    </#if>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#if topicRequest.topic??>
        <#include "../common/serverOpStatus.ftl">

        <br/>

        <#include "../common/entityYaml.ftl">
    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
