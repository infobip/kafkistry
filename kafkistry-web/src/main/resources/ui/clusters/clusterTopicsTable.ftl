<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->

<#import "../common/util.ftl" as util>

<#assign datatableId = "topics">
<#include "../common/loading.ftl">
<table id="${datatableId}" class="table table-hover table-bordered datatable" style="display: none;">
    <thead class="table-theme-dark">
    <tr>
        <th>Topic name</th>
        <th style="width: 200px;">Topic status</th>
        <th>Action</th>
    </tr>
    </thead>
    <tbody>
    <#if clusterTopics.statusPerTopics??>
        <#list clusterTopics.statusPerTopics as topicStatus>
            <#assign topicName = topicStatus.topicName>
            <#assign statusTypes = util.namedTypeListToStringList(topicStatus.topicClusterStatus.status.types)>
            <#assign presentInRegistry = !statusTypes?seq_contains("UNKNOWN")>
            <tr class="topic-row table-row">
                <td>
                    <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)}"
                       title="Inspect this topic on this cluster...">
                            <#if presentInRegistry>
                                <span class="font-weight-bold">${topicStatus.topicName}</span>
                            <#else>
                                <span class="text-info">${topicStatus.topicName}</span>
                            </#if>
                    </a>
                </td>
                <td style="width: 200px;">
                    <#assign topicOnClusterStatus = topicStatus.topicClusterStatus.status>
                    <#include "../common/topicOnClusterStatus.ftl">
                </td>
                <td>
                    <#assign availableActions = topicStatus.topicClusterStatus.status.availableActions>
                    <#include "../common/topicOnClusterAction.ftl">
                </td>
            </tr>
        </#list>
    </#if>
    </tbody>
</table>
