<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterGroups"  type="com.infobip.kafkistry.service.consumers.ClusterConsumerGroups" -->

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as statusUtil>

<#assign datatableId = "consumer-groups-table">
<#include "../common/loading.ftl">

<table id="${datatableId}" class="table table-bordered datatable" style="display: none;">
    <thead class="thead-dark">
    <tr>
        <th>Group</th>
        <th>Status</th>
        <th>Assignor</th>
        <th>Lag</th>
    </tr>
    </thead>
    <#list clusterGroups.consumerGroups as consumerGroup>
        <tr>
            <td>
                <a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, consumerGroup.groupId)}"
                   class="btn btn-sm btn-outline-dark mb-1">
                    ${consumerGroup.groupId}
                </a>
            </td>
            <td>
                <#assign class = statusUtil.consumerStatusAlertClass(consumerGroup.status)>
                <div class="alert ${class} alert-cell">
                    ${consumerGroup.status}
                </div>
            </td>
            <td>
                ${consumerGroup.partitionAssignor}
            </td>
            <td>
                <#assign class = statusUtil.lagStatusAlertClass(consumerGroup.lag.status)>
                <div class="alert ${class} alert-cell">
                    ${consumerGroup.lag.status}
                </div>
            </td>
        </tr>
    </#list>
</table>
