<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->

<#import "../common/util.ftl" as util>

<#assign datatableId = "topics-table">
<#include "../common/loading.ftl">
<table id="${datatableId}" class="table table-bordered datatable" style="display: none;">
    <thead class="thead-dark">
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
            <#assign statusTypes = util.enumListToStringList(topicStatus.status.types)>
            <#assign presentInRegistry = !statusTypes?seq_contains("UNKNOWN")>
            <tr class="topic-row table-row">
                <td>
                    <a href="${appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)}">
                        <button class="btn btn-sm btn-outline-dark"
                                title="Inspect this topic on this cluster...">
                            <#if presentInRegistry>
                                <span class="font-weight-bold">${topicStatus.topicName} 🔍</span>
                            <#else>
                                <span class="text-info">${topicStatus.topicName} 🔍</span>
                            </#if>
                        </button>
                    </a>
                </td>
                <td style="width: 200px;">
                    <#assign topicOnClusterStatus = topicStatus.status>
                    <#include "../common/topicOnClusterStatus.ftl">
                </td>
                <td>
                    <#assign availableActions = topicStatus.status.availableActions>
                    <#include "../common/topicOnClusterAction.ftl">
                </td>
            </tr>
        </#list>
    </#if>
    </tbody>
</table>
