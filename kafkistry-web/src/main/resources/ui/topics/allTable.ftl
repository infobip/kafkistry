<#-- @ftlvariable name="topics"  type="java.util.List<com.infobip.kafkistry.service.topic.TopicStatuses>" -->
<#-- @ftlvariable name="topicsOwned"  type="java.util.Map<java.lang.String, java.lang.Boolean>" -->

<#import "../common/util.ftl" as util>

<#assign datatableId = "topics">
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
            <#assign topicName = topic.topicName>
            <#assign topicOwned = topicsOwned[topicName]>
            <td data-toggle="tooltip" data-order="${topicOwned?then("0", "1")}_${topicName}">
                <a href="${appUrl.topics().showTopic(topicName)}">
                    ${topicName}
                </a>
            </td>
            <#if topic.topicDescription??>
                <td data-order="${topicOwned?then("0", "1")}_${topic.topicDescription.owner}">
                    ${topic.topicDescription.owner}
                    <#if topicOwned>
                        <@util.yourOwned what="topic"/>
                    </#if>
                </td>
                <td><@util.presence presence = topic.topicDescription.presence inline = false/></td>
            <#else>
                <td><span class="text-primary text-monospace small">[none]</span></td>
                <td><span class="text-primary text-monospace small">[undefined]</span></td>
            </#if>
            <td>
                <#list topic.topicsStatusCounts as statusTypeCount>
                    <@util.namedTypeStatusAlert type = statusTypeCount.type quantity = statusTypeCount.quantity/>
                </#list>
            </td>
        </tr>
    </#list>
    </tbody>
</table>

<div id="all-topics-data" style="display: none">
    <#-- for clone dropdown suggested names -->
    <#list topics as topic>
        <#if topic.topicDescription??>
            <div class="topicName" data-topic-name="${topic.topicDescription.name}"></div>
        </#if>
    </#list>
</div>
