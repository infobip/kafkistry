<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html>

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/topicForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/topicResourceRequirements.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/topicLabels.js?ver=${lastCommit}"></script>
    <script src="static/topic/createTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create new topic</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <#assign newName = "">

    <h1>Create Topic</h1>

    <hr>

    <#include "form/topicForm.ftl">

    <br/>

    <button id="create-btn" class="btn btn-primary btn-sm">Save new</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">
    <br/>

    <#include "../common/entityYaml.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
