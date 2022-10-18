<#-- @ftlvariable name="clusterGroups"  type="com.infobip.kafkistry.service.consumers.ClusterConsumerGroups" -->

<div class="m-3">
    <#assign consumersStats = clusterGroups.consumersStats>
    <#include "../consumers/consumerGroupsCounts.ftl">
</div>
<hr/>
<#include "clusterConsumerGroupsTable.ftl">