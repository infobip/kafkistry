<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#macro resource resource>
<#-- @ftlvariable name="resource" type="com.infobip.kafkistry.model.AclResource" -->
    <span class="badge badge-dark">${resource.type}</span>
    ${resource.name}<#if resource.namePattern.name() == "PREFIXED"><code>*</code></#if>
</#macro>

<#macro operation type>
<#-- @ftlvariable name="type" type="com.infobip.kafkistry.model.AclOperation.Type" -->
    <span class="badge badge-dark">${type}</span>
</#macro>

<#macro policy policy>
<#-- @ftlvariable name="policy" type="com.infobip.kafkistry.model.AclOperation.Policy" -->
    <#switch policy>
        <#case "ALLOW">
            <span class="badge badge-success">ALLOW</span>
            <#break>
        <#case "DENY">
            <span class="badge badge-danger">DENY</span>
            <#break>
        <#default>
            <span>${policy}</span>
    </#switch>
</#macro>

<#function operationBtnClass operation>
<#-- @ftlvariable name="operation" type="com.infobip.kafkistry.service.acl.AvailableAclOperation" -->
    <#switch operation>
        <#case "CREATE_MISSING_ACLS">
            <#return "btn-outline-info">
        <#case "DELETE_UNWANTED_ACLS">
            <#return "btn-outline-danger">
        <#case "EDIT_PRINCIPAL_ACLS">
            <#return "btn-outline-primary">
        <#case "IMPORT_PRINCIPAL">
            <#return "btn-outline-primary">
        <#default>
            <#return "">
    </#switch>
</#function>

<!--
Level:
  - PRINCIPAL_RULE_ON_CLUSTER   rule != ""
  - PRINCIPAL_ON_CLUSTER        rule == ""
-->
<#macro availableOperation operation principal cluster rule>
<#-- @ftlvariable name="operation" type="com.infobip.kafkistry.service.acl.AvailableAclOperation" -->
<#-- @ftlvariable name="principal" type="java.lang.String" -->
<#-- @ftlvariable name="cluster" type="java.lang.String" -->
<#-- @ftlvariable name="rule" type="java.lang.String" OPTIONAL -->

    <#assign url = "">
    <#assign btnClass = operationBtnClass(operation)>
    <#assign btnText = "---">
    <#switch operation>
        <#case "CREATE_MISSING_ACLS">
            <#if rule != "">
                <#assign btnText = "Create rule...">
                <#assign url = appUrl.acls().showCreatePrincipalRules(principal, cluster, rule)>
            <#else>
                <#assign btnText = "Create rules...">
                <#assign url = appUrl.acls().showCreatePrincipalRules(principal, cluster)>
            </#if>
            <#break>
        <#case "DELETE_UNWANTED_ACLS">
            <#if rule != "">
                <#assign btnText = "Delete rule...">
                <#assign url = appUrl.acls().showDeletePrincipalRules(principal, cluster, rule)>
            <#else>
                <#assign btnText = "Delete rules...">
                <#assign url = appUrl.acls().showDeletePrincipalRules(principal, cluster)>
            </#if>
            <#break>
        <#case "EDIT_PRINCIPAL_ACLS">
            <#assign btnText = "Suggested edit...">
            <#assign url = appUrl.acls().showSuggestedEditPrincipal(principal)>
            <#break>
        <#case "IMPORT_PRINCIPAL">
            <#assign btnText = "Import principal ACLs...">
            <#assign url = appUrl.acls().showImportPrincipal(principal)>
            <#break>
    </#switch>
    <a class="btn btn-sm ${btnClass} mt-1" href="${url}">
        ${btnText}
    </a>
</#macro>
