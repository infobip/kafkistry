<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html>

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/topicForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/topicResourceRequirements.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/editTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: ${title}</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">
    <h1><#include "../common/backBtn.ftl"> ${title}</h1>

    <hr>

    <#include "form/topicForm.ftl">

    <br/>

    <button class="btn btn-primary btn-sm" id="edit-btn">Save changes</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">

    <br/>

    <#include "../common/entityYaml.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>