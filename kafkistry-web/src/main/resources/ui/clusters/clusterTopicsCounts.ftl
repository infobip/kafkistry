<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->
<#import "../common/util.ftl" as util>
<#if clusterTopics.topicsStatusCounts??>
    <table class="table table-hover table-sm m-0">
        <#list clusterTopics.topicsStatusCounts as statusTypeCount>
            <#assign statusType = statusTypeCount.type>
            <#assign count = statusTypeCount.quantity>
            <tr>
                <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType.name}"
                    title="Click to filter by..." data-table-id="topics">
                    <@util.namedTypeStatusAlert type=statusType alertInline=false/>
                </td>
                <td style="text-align: right;">${count}</td>
            </tr>
        </#list>
    </table>
<#else>
    <i>(no data)</i>
</#if>