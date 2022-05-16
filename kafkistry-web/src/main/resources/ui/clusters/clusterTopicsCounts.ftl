<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->

<#if clusterTopics.topicsStatusCounts??>
    <table class="table table-sm m-0">
        <#list clusterTopics.topicsStatusCounts as statusType, count>
            <tr>
                <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType}"
                    title="Click to filter by..." data-table-id="topics-table">
                    <#include "../common/topicStatusResultBox.ftl">
                </td>
                <td style="text-align: right;">${count}</td>
            </tr>
        </#list>
    </table>
<#else>
    <i>(no data)</i>
</#if>