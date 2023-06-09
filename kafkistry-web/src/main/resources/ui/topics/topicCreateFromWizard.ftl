<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "form/topicFormResources.ftl">
    <script src="static/topic/createTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create new topic from wizard</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">

    <h1>Create new topic from wizard</h1>
    <hr>
    <#assign newName = topic.name>
    <#include "form/topicForm.ftl">
    <br/>
    <button id="create-btn" class="btn btn-primary btn-sm">Create</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">

    <br/>
    <#include "../common/entityYaml.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
