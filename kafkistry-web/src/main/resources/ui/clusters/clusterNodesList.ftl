<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->

<#macro clusterNodeId nodeId>
    <#assign online = clusterInfo.onlineNodeIds?seq_contains(nodeId)>
    <#assign controller = clusterInfo.controllerId == nodeId>
    <#assign rackSet = false>
    <#assign processRoles = []>
    <#list clusterInfo.nodes as node>
        <#if node.nodeId == nodeId>
            <#assign processRoles = node.roles>
            <#if node.rack??>
                <#assign rackSet = true>
                <#assign brokerRack = node.rack>
            </#if>
        </#if>
    </#list>
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
        <#assign isBroker = processRoles?seq_contains("BROKER")>
        <#assign isController = processRoles?seq_contains("CONTROLLER")>
        <#assign availableStatus = online?then("ONLINE", "OFFLINE")>
        <#assign tooltip = processRoles?join("&")?lower_case + " ${availableStatus}, quorum role: ${quorumRole}">
        <#if !online>
            <#assign alertClass = "alert-danger">
        <#elseif isBroker && isController>
            <#assign alertClass = "alert-primary">
        <#elseif voter>
            <#assign alertClass = "alert-info">
        <#elseif observer>
            <#assign alertClass = "alert-success">
        <#else>
            <#assign alertClass = "alert-warning">
        </#if>
    <#else>
        <#assign alertClass = controller?then("alert-primary", online?then("alert-success", "alert-danger"))>
        <#assign tooltip = controller?then("node ${nodeId}: CONTROLLER", online?then("broker ${nodeId}: ONLINE", "node ${nodeId}: OFFLINE"))>
    </#if>
    <span class="alert alert-sm alert-inline m-0 p-1 pl-2 pr-2 text-center ${alertClass}" title="${tooltip}">
        ${nodeId?c}
        <#if rackSet>
            <br/>
            <span class="small" title="Broker rack"><code>${brokerRack}</code></span>
        </#if>
    </span>
</#macro>

<#list clusterInfo.nodeIds as nodeId>
    <@clusterNodeId nodeId=nodeId/>
</#list>