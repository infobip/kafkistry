<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html>

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/topicForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/topicResourceRequirements.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/createTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create new topic by clone</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">

    <h1><#include  "../common/backBtn.ftl"> Create new topic by clone</h1>
    <hr>
    <p>Cloning from topic: ${topic.name}</p>
    <#assign newName = "">
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