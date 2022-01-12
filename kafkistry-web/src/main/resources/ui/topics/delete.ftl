<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/deleteTopicInRegistry.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Delete topic</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<div class="container">
    <h1>Delete topic from registry</h1>
    <hr>

    <br/>
    <p>Deleting topic name: ${topic.name}</p>
    <label>
        Delete reason message <@info.icon tooltip=doc.updateInputMsg/>:
        <input class="width-full" id="delete-message" type="text">
    </label>
    <br/>
    <#if gitStorageEnabled>
        <label>
            Choose branch to write into:
            <input class="width-full" name="targetBranch" placeholder="custom branch name or (empty) for default">
        </label>
        <br/>
    </#if>

    <button class="btn btn-danger btn-sm" id="delete-btn" data-topic-name="${topic.name}">
        Delete topic from registry
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
