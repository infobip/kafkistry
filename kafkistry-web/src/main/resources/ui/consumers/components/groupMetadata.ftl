<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="newConsumerGroup" type="java.lang.Boolean" -->

<#import "../../common/util.ftl" as util>
<#import "../../common/infoIcon.ftl" as info>
<#import "../../common/documentation.ftl" as doc>
<#import "../util.ftl" as statusUtil>

<table class="table table-sm m-0">
    <tr>
        <th>Group Id</th>
        <td class="h6"><a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroupId)}">${consumerGroupId}</a></td>
    </tr>
    <tr>
        <th>Cluster</th>
        <td><a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a></td>
    </tr>
    <#if (newConsumerGroup!false)>
        <tr>
            <td colspan="100">
                <div class="alert alert-info">
                    New consumer group, no metadata to show
                </div>
            </td>
        </tr>
    <#elseif !consumerGroup??>
        <tr>
            <td colspan="100">
                <div class="alert alert-danger">
                    Could not find/read this consumer group on this cluster
                </div>
            </td>
        </tr>
    <#else>
        <tr>
            <th>Status</th>
            <td>
                <div class="alert alert-inline alert-sm mb-0 ${statusUtil.consumerStatusAlertClass(consumerGroup.status)}">
                    ${consumerGroup.status}
                </div>
            </td>
        </tr>
        <tr>
            <th>Partition assignor</th>
            <td>${consumerGroup.partitionAssignor}</td>
        </tr>
        <tr>
            <th>Lag</th>
            <td>
                <@util.namedTypeStatusAlert type=consumerGroup.lag.status/>
                (total: ${consumerGroup.lag.amount!'N/A'})
            </td>
        </tr>
        <tr>
            <th>Number topics</th>
            <td>${consumerGroup.topicMembers?size}</td>
        </tr>
    </#if>
</table>
