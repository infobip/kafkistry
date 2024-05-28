<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->

<#macro clusterBrokerId brokerId>
    <#assign online = clusterInfo.onlineNodeIds?seq_contains(brokerId)>
    <#assign controller = clusterInfo.controllerId == brokerId>
    <#if clusterInfo.kraftEnabled>
        <#assign voter = false>
        <#assign observer = false>
        <#list clusterInfo.quorumInfo.voters as replica>
            <#assign voter = voter || replica.replicaId == brokerId>
        </#list>
        <#list clusterInfo.quorumInfo.observers as replica>
            <#assign observer = observer || replica.replicaId == brokerId>
        </#list>
        <#assign quorumRole = voter?then("VOTER", observer?then("OBSERVER", "NONE"))>
        <#assign availableStatus = online?then("ONLINE", "OFFLINE")>
        <#assign tooltip = "broker ${availableStatus}, quorum role: ${quorumRole}">
        <#if !online>
            <#assign alertClass = "alert-danger">
        <#elseif voter>
            <#assign alertClass = "alert-success">
        <#elseif observer>
            <#assign alertClass = "alert-info">
        <#else>
            <#assign alertClass = "alert-warning">
        </#if>
    <#else>
        <#assign alertClass = controller?then("alert-primary", online?then("alert-success", "alert-danger"))>
        <#assign tooltip = controller?then("broker ${brokerId}: CONTROLLER", online?then("broker ${brokerId}: ONLINE", "broker ${brokerId}: OFFLINE"))>
    </#if>
    <span class="alert alert-sm alert-inline m-0 p-1 pl-2 pr-2 ${alertClass}" title="${tooltip}">${brokerId}</span>
</#macro>

<#list clusterInfo.nodeIds as brokerId>
    <@clusterBrokerId brokerId=brokerId/>
</#list>