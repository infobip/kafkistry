<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="forceTagForPresence" type="java.lang.Boolean" -->
<#-- @ftlvariable name="enums"  type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, ? extends java.lang.Object>>" -->


<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "form/topicFormResources.ftl">
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

    <#assign tagOnly = forceTagForPresence!false>
    <#if tagOnly && topic.presence.type != "TAGGED_CLUSTERS">
        <div class="alert alert-warning">
            <strong>Not allowed clone a topic exactly</strong><br/>
            <span>Source topic uses presence type <code>${topic.presence.type}</code> which is not allowed.</span><br/>
            <span>Please switch to using cluster tags for defining topic's presence.</span><br/>
            <span>Defaulting to presence type <code>TAGGED_CLUSTERS</code></span><br/>
            <span>Source topic's presence was:</span> <code>${topic.presence.toString()}</code>
        </div>
        <#assign presenceTypes = enums["com.infobip.kafkistry.model.PresenceType"]>
        <#list presenceTypes as presenceType, enum>
            <#if presenceType.toString() == "TAGGED_CLUSTERS">
                <#assign taggedClustersEnum = presenceType>
            </#if>
        </#list>
        <#assign topic = topic + {'presence': {'type':taggedClustersEnum}}>
    </#if>

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
