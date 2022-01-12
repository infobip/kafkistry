<#-- @ftlvariable name="topicOnClusterStatus" type="com.infobip.kafkistry.service.TopicOnClusterInspectionResult" -->

<#assign detailedStatuses = []>
<#list (topicOnClusterStatus.wrongValues)![] as wrongValue>
    <#assign detailedStatuses = detailedStatuses + [wrongValue.type]>
</#list>
<#list (topicOnClusterStatus.ruleViolations)![] as ruleViolation>
    <#assign detailedStatuses = detailedStatuses + [ruleViolation.type]>
</#list>
<#list (topicOnClusterStatus.currentConfigRuleViolations)![] as ruleViolation>
    <#assign detailedStatuses = detailedStatuses + [ruleViolation.type]>
</#list>

<#list topicOnClusterStatus.types as statusType>
    <#if !(detailedStatuses?seq_contains(statusType))>
        <#include "topicStatusResultBox.ftl">
        <#if !(statusType?is_last)><br/></#if>
    </#if>
</#list>
<#if topicOnClusterStatus.wrongValues??>
    <#list topicOnClusterStatus.wrongValues as wrongValue>
        <#if wrongValue?is_first><br/></#if>
        <#assign statusType = wrongValue.type>
        <#include "topicStatusResultBox.ftl">
        <div class="text-break">
            <strong>What:</strong> "${wrongValue.key}"<br/>
            <strong>Expected:</strong>
            <#if wrongValue.expectedDefault>
                <i>(to be default / not-overridden)</i>
            <#else>
                <code>
                    <span class="conf-value" data-name="${wrongValue.key}"
                          data-value="${wrongValue.expected}">${wrongValue.expected}</span>
                </code>
            </#if>
            <br/>
            <strong>Actual:</strong>
            <code>
                <span class="conf-value" data-name="${wrongValue.key}"
                      data-value="${wrongValue.actual}">${wrongValue.actual}</span>
            </code>
            <#if wrongValue.message??>
                <br/>
                <strong>Message:</strong> ${wrongValue.message}
            </#if>
        </div>
        <hr/>
    </#list>
</#if>

<#macro valueMacro placeholderName placeholders>
<#-- @ftlvariable name="placeholders" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.validation.rules.Placeholder>" -->
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
        <#case "WARNING">
            <#return "badge-warning">
        <#case "ERROR">
            <#return "badge-danger">
        <#default>
            <#return "badge-secondary">
    </#switch>
</#function>

<#macro interpretMessage violation>
<#-- @ftlvariable name="violation" type="com.infobip.kafkistry.service.RuleViolationIssue" -->
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

<#if topicOnClusterStatus.ruleViolations??>
    <#list topicOnClusterStatus.ruleViolations as ruleViolation>
        <#assign statusType = ruleViolation.type><#include "topicStatusResultBox.ftl">
        <br/>
        <@interpretMessage violation=ruleViolation/>
        <hr/>
    </#list>
</#if>
<#if topicOnClusterStatus.currentConfigRuleViolations??>
    <#list topicOnClusterStatus.currentConfigRuleViolations as ruleViolation>
        <#assign statusType = ruleViolation.type><#include "topicStatusResultBox.ftl">
        <br/>
        <@interpretMessage violation=ruleViolation/>
        <hr/>
    </#list>
</#if>
