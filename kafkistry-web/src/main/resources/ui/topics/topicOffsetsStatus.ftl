<#-- @ftlvariable name="topicOffsets" type="com.infobip.kafkistry.service.topic.offsets.TopicOffsets" -->

<#import "../common/util.ftl" as toUtil>

<div class="card">
    <div class="card-header">
        <span class="h4">Messages stats</span>
    </div>
    <div class="card-body p-1">
        <#if topicOffsets.empty>
            <div class="alert alert-warning">
                Topic is empty
            </div>
        <#else>
            <div class="alert alert-info">
                Total messages in all partitions: ~<strong>${toUtil.prettyNumber(topicOffsets.size)}</strong>
                <#include "numMessagesEstimateDoc.ftl">
            </div>
        </#if>
        <#if topicOffsets.messagesRate??>
            <#assign rate = topicOffsets.messagesRate>
            <table class="table table-hover table-sm" style="table-layout: fixed;">
                <thead class="">
                <tr>
                    <th colspan="10" class="alert-secondary text-center">Avg msg/sec rate in last</th>
                </tr>
                <tr>
                    <th>15sec</th>
                    <th>1min</th>
                    <th>5min</th>
                    <th>15min</th>
                    <th>30min</th>
                    <th>1h</th>
                    <th>2h</th>
                    <th>6h</th>
                    <th>12h</th>
                    <th>24h</th>
                </tr>
                </thead>
                <tr>
                    <td>
                        <#if rate.last15Sec??>~${toUtil.prettyNumber(rate.last15Sec)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.lastMin??>~${toUtil.prettyNumber(rate.lastMin)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last5Min??>~${toUtil.prettyNumber(rate.last5Min)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last15Min??>~${toUtil.prettyNumber(rate.last15Min)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last30Min??>~${toUtil.prettyNumber(rate.last30Min)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.lastH??>~${toUtil.prettyNumber(rate.lastH)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last2H??>~${toUtil.prettyNumber(rate.last2H)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last6H??>~${toUtil.prettyNumber(rate.last6H)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last12H??>~${toUtil.prettyNumber(rate.last12H)}<#else>N/A</#if>
                    </td>
                    <td>
                        <#if rate.last24H??>~${toUtil.prettyNumber(rate.last24H)}<#else>N/A</#if>
                    </td>
                </tr>
            </table>
        </#if>
    </div>
</div>
