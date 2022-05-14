<#macro valueMacro placeholderName placeholders>
<#-- @ftlvariable name="placeholders" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.Placeholder>" -->
    <#assign placeholder = placeholders[placeholderName]><#t>
    <code><#t>
    <#assign valueRaw><#t>
        <#if !(placeholder.value??)><#t>
            null<#t>
        <#elseif placeholder.value?is_string><#t>
            ${placeholder.value}<#t>
        <#else><#t>
            ${placeholder.value?c}<#t>
        </#if><#t>
    </#assign><#t><span class="conf-value" data-name="${placeholder.key}"
                        data-value="${valueRaw}" title="${placeholder.key}">${valueRaw}</span><#t>
    </code><#t>
</#macro>

<#function severityClass severity>
    <#switch severity.name()>
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
    <#assign messageTemplate = violation.message?replace(
    "%(\\w+)%",
    "<@valueMacro placeholderName='$1' placeholders=placeholders/>",
    "r"
    )?interpret placeholders = violation.placeholders>
    <#assign ruleSimpleName = violation.ruleClassName?substring(violation.ruleClassName?last_index_of(".") + 1)>
    <strong>Rule</strong>: <span class="small text-monospace" title="${violation.ruleClassName}">${ruleSimpleName}</span><br/>
    <strong>Severity</strong>:
    <span class="badge ${severityClass(violation.severity)}">${violation.severity}</span>
    <br/>
    <strong>Message</strong>: <@messageTemplate/>
</#macro>

