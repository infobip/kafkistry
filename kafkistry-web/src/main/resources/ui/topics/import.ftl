<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "form/topicFormResources.ftl">
    <script src="static/topic/importTopic.js?ver=${lastCommit}"></script>
    <script src="static/topic/createTopic.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Import topic suggestion</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">

    <h1>Import topic suggestion</h1>
    <hr>
    <#include "form/topicForm.ftl">
    <br/>
    <button id="import-btn" class="btn btn-primary btn-sm">Import to registry</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">

    <br/>
    <#include "../common/entityYaml.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
