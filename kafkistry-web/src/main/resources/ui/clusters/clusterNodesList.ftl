<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->

<#macro clusterBrokerId brokerId>
    <#assign online = clusterInfo.onlineNodeIds?seq_contains(brokerId)>
    <#assign controller = clusterInfo.controllerId == brokerId>
    <#assign alertClass = controller?then("alert-primary", online?then("alert-success", "alert-danger"))>
    <#assign tooltip = controller?then("broker ${brokerId}: CONTROLLER", online?then("broker ${brokerId}: ONLINE", "broker ${brokerId}: OFFLINE"))>
    <span class="alert alert-sm alert-inline m-0 p-1 pl-2 pr-2 ${alertClass}" title="${tooltip}">${brokerId}</span>
</#macro>

<#list clusterInfo.nodeIds as brokerId>
    <@clusterBrokerId brokerId=brokerId/>
</#list>