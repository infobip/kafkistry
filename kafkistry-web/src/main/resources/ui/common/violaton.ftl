<#macro valueMacro placeholderName placeholders>
<#-- @ftlvariable name="placeholders" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.Placeholder>" -->
    <#assign placeholder = placeholders[placeholderName]><#t>
    <code><#t>
    <#assign valueRaw><#t>
        <#if !(placeholder.value??)><#t>
            null<#t>
        <#elseif placeholder.value?is_string><#t>
            ${placeholder.value}<#t>
        <#elseif placeholder.value?is_sequence><#t>
            [${placeholder.value?join(", ")}]<#t>
        <#else><#t>
            ${placeholder.value?c}<#t>
        </#if><#t>
    </#assign><#t><span class="conf-value conf-in-message" data-name="${placeholder.key}"
                        data-value="${valueRaw}" title="${placeholder.key}: ${valueRaw}">${valueRaw}</span><#t>
    </code><#t>
</#macro>

<#macro richMessage message placeholders>
<#-- @ftlvariable name="message" type="java.lang.String" -->
<#-- @ftlvariable name="placeholders" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.Placeholder>" -->
    <#assign messageTemplate = message?replace(
    "%(\\w+)%",
    "<@valueMacro placeholderName='$1' placeholders=placeholders/>",
    "r"
    )?interpret placeholders = placeholders>
    <@messageTemplate/>
</#macro>

<#function severityClass severity>
    <#switch severity>
        <#case "NONE">
            <#return "badge-success">
        <#case "MINOR">
            <#return "badge-info">
        <#case "WARNING">
            <#return "badge-warning">
        <#case "CRITICAL">
        <#case "ERROR">
            <#return "badge-danger">
        <#default>
            <#return "badge-secondary">
    </#switch>
</#function>

<#macro interpretMessage violation>
<#-- @ftlvariable name="violation" type="com.infobip.kafkistry.service.RuleViolation" -->
    <#assign ruleSimpleName = violation.ruleClassName?substring(violation.ruleClassName?last_index_of(".") + 1)>
    <strong>Rule</strong>: <span class="small text-monospace" title="${violation.ruleClassName}">${ruleSimpleName}</span><br/>
    <strong>Severity</strong>:
    <span class="badge ${severityClass(violation.severity)}">${violation.severity}</span>
    <br/>
    <strong>Message</strong>: <@richMessage message=violation.message placeholders = violation.placeholders/>
</#macro>



