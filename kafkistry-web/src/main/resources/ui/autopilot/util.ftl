<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#macro outcomeBadge outcomeType>
<#-- @ftlvariable name="outcomeType" type="com.infobip.kafkistry.autopilot.reporting.ActionOutcome.OutcomeType" -->
    <#import "../common/infoIcon.ftl" as info>
    <#assign badgeClass = "">
    <#switch outcomeType>
        <#case "DISABLED">
            <#assign badgeClass = "badge-secondary">
            <#break>
        <#case "CLUSTER_UNSTABLE">
        <#case "BLOCKED">
            <#assign badgeClass = "badge-warning">
            <#break>
        <#case "PENDING">
            <#assign badgeClass = "badge-info">
            <#break>
        <#case "NOT_ACQUIRED">
            <#assign badgeClass = "badge-dark">
            <#break>
        <#case "FAILED">
            <#assign badgeClass = "badge-danger">
            <#break>
        <#case "RESOLVED">
            <#assign badgeClass = "badge-success">
            <#break>
        <#case "SUCCESSFUL">
            <#assign badgeClass = "badge-success">
            <#break>
    </#switch>
    <span class="badge ${badgeClass}">${outcomeType} <@info.icon tooltip=outcomeType.doc/></span>
</#macro>

<#function resolveLink metadata>
<#-- @ftlvariable name="metadata" type="com.infobip.kafkistry.autopilot.binding.ActionMetadata" -->
    <#assign attributes = metadata.attributes>
    <#switch metadata.description.targetType>
        <#case "TOPIC">
            <#return appUrl.topics().showInspectTopicOnCluster(attributes["topicName"], attributes["clusterIdentifier"])>
        <#case "ACL">
            <#return appUrl.acls().showAllPrincipalAcls(attributes["principal"], attributes["aclRule"], attributes["clusterIdentifier"])>
    </#switch>
    <#return "">
</#function>