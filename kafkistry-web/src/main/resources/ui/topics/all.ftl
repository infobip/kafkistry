<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topics"  type="java.util.List<com.infobip.kafkistry.service.topic.TopicStatuses>" -->
<#-- @ftlvariable name="pendingTopicsUpdates"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.TopicRequest>>" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Topics</title>
    <meta name="current-nav" content="nav-topics"/>
    <meta name="current-topic-nav" content="topic-nav-by-name"/>
    <script src="static/topic/topicsAll.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>

<div class="container">
    <#if gitStorageEnabled>
        <#assign pendingUpdates = pendingTopicsUpdates>
        <#assign entityName = "Topic">
        <#include "../common/pendingChangeRequests.ftl">
        <br/>
    </#if>

    <div class="card">

    <div class="card-header">
        <span class="h4">Status of all topics in registry</span>
        <div class="float-right">
            <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                Create new topic...
            </button>
            <div class="dropdown-menu open">
                <a class="dropdown-item" href="${appUrl.topics().showTopicWizard()}">Wizard</a>
                <a class="dropdown-item" href="${appUrl.topics().showTopicCreate()}">Manual setup</a>
                <a class="dropdown-item" id="clone-existing-btn" href>Clone existing</a>
                <div class="clone-existing-container" style="display: none;">
                    <form class="form-inline mb-0" action="${appUrl.topics().showCloneAddNewTopic()}" method="get">
                        <label>
                            <input id="cloneInput" class="m-2" style="width: 300px;" type="search"
                                   name="topicName" placeholder="Choose existing topic...">
                        </label>
                    </form>
                </div>
            </div>
            <a class="btn btn-outline-info ml-2" href="${appUrl.compare().showComparePage()}">Compare...</a>
        </div>
    </div>

    <div class="card-body pl-0 pr-0">

    <#assign datatableId = "topics-table">
    <#include "../common/loading.ftl">
    <table id="${datatableId}" class="table table-bordered datatable m-0" style="display: none;">
        <thead class="thead-dark">
        <tr>
            <th scope="col">Topic</th>
            <th scope="col">Owner</th>
            <th scope="col">Presence</th>
            <th scope="col">Status</th>
        </tr>
        </thead>
        <tbody>
        <#list topics as topic>
            <tr class="topic-row table-row">
                <td data-toggle="tooltip">
                    <#assign topicName = topic.topicName>
                    <a href="${appUrl.topics().showTopic(topicName)}">
                        ${topicName}
                    </a>
                </td>
                <#if topic.topicDescription??>
                    <td>${topic.topicDescription.owner}</td>
                    <td><@util.presence presence = topic.topicDescription.presence inline = false/></td>
                <#else>
                    <td><span class="text-primary text-monospace small">[none]</span></td>
                    <td><span class="text-primary text-monospace small">[undefined]</span></td>
                </#if>
                <td>
                    <#assign statusFlags = topic.aggStatusFlags>
                    <#assign clusterStatusFlags = util.clusterStatusFlags(topic.statusPerClusters)>
                    <#assign allTopicStatusTypes = util.allTopicStatusTypes(topic.statusPerClusters)>
                    <#assign nonOkTopicStatusTypes = util.nonOkTopicStatusTypes(topic.statusPerClusters)>
                    <#assign asStatusFlag = true>
                    <#include "../common/statusFlags.ftl">
                </td>
            </tr>
        </#list>
        </tbody>
    </table>
    </div>

    </div>

    <div id="all-topics-data" style="display: none">
        <#-- for clone dropdown suggested names -->
        <#list topics as topic>
            <#if topic.topicDescription??>
                <div class="topicName" data-topic-name="${topic.topicDescription.name}"></div>
            </#if>
        </#list>
    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
