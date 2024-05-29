<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->

<#macro clusterNodeId nodeId>
    <#assign online = clusterInfo.onlineNodeIds?seq_contains(nodeId)>
    <#assign controller = clusterInfo.controllerId == nodeId>
    <#if clusterInfo.kraftEnabled>
        <#assign voter = false>
        <#assign observer = false>
        <#list clusterInfo.quorumInfo.voters as replica>
            <#assign voter = voter || replica.replicaId == nodeId>
        </#list>
        <#list clusterInfo.quorumInfo.observers as replica>
            <#assign observer = observer || replica.replicaId == nodeId>
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
        <#assign tooltip = controller?then("node ${nodeId}: CONTROLLER", online?then("broker ${nodeId}: ONLINE", "node ${nodeId}: OFFLINE"))>
    </#if>
    <span class="alert alert-sm alert-inline m-0 p-1 pl-2 pr-2 ${alertClass}" title="${tooltip}">${nodeId}</span>
</#macro>

<#list clusterInfo.nodeIds as nodeId>
    <@clusterNodeId nodeId=nodeId/>
</#list>