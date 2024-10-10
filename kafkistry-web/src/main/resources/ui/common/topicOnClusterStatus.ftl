<#-- @ftlvariable name="topicOnClusterStatus" type="com.infobip.kafkistry.service.topic.TopicOnClusterInspectionResult" -->

<#import "../common/util.ftl" as util>

<#assign detailedStatuses = []>
<#list (topicOnClusterStatus.wrongValues)![] as wrongValue>
    <#assign detailedStatuses = detailedStatuses + [wrongValue.type]>
</#list>
<#list (topicOnClusterStatus.updateValues)![] as updateValue>
    <#assign detailedStatuses = detailedStatuses + [updateValue.type]>
</#list>
<#list (topicOnClusterStatus.ruleViolations)![] as ruleViolation>
    <#assign detailedStatuses = detailedStatuses + [ruleViolation.type]>
</#list>
<#list (topicOnClusterStatus.currentConfigRuleViolations)![] as ruleViolation>
    <#assign detailedStatuses = detailedStatuses + [ruleViolation.type]>
</#list>
<#list topicOnClusterStatus.typeDescriptions as typeDescription>
    <#assign detailedStatuses = detailedStatuses + [typeDescription.type]>
</#list>

<#list topicOnClusterStatus.types as statusType>
    <#if !(detailedStatuses?seq_contains(statusType))>
        <@util.namedTypeStatusAlert type=statusType/>
    </#if>
</#list>

<#assign alertInline = false>

<#macro valueAssertDetail valueAssertion>
    <#-- @ftlvariable name="valueAssertion" type="com.infobip.kafkistry.service.topic.WrongValueAssertion" -->
    <#assign statusType = valueAssertion.type>
    <@util.namedTypeStatusAlert type=statusType alertInline=false/>
    <#assign wrongStatusType = !statusType.valid>
    <div class="text-break">
        <strong>What:</strong> "${valueAssertion.key}"<br/>
        <#if wrongStatusType>
            <strong>Expected:</strong>
        <#else>
            <strong>New:</strong>
        </#if>
        <#if valueAssertion.expectedDefault>
            <i>(to be default / not-overridden)</i>
        <#else>
            <code>
                <span class="conf-value" data-name="${valueAssertion.key}"
                      data-value="${valueAssertion.expected}">${valueAssertion.expected}</span>
            </code>
        </#if>
        <br/>
        <#if wrongStatusType>
            <strong>Actual:</strong>
        <#else>
            <strong>Old:</strong>
        </#if>
        <code>
            <span class="conf-value" data-name="${valueAssertion.key}"
                  data-value="${valueAssertion.actual}">${valueAssertion.actual}</span>
        </code>
        <#if valueAssertion.message??>
            <br/>
            <strong>Message:</strong> ${valueAssertion.message}
        </#if>
    </div>
    <hr/>
</#macro>

<#if topicOnClusterStatus.wrongValues??>
    <#list topicOnClusterStatus.wrongValues as wrongValue>
        <#if wrongValue?is_first><br/></#if>
        <@valueAssertDetail valueAssertion=wrongValue/>
    </#list>
</#if>
<#if topicOnClusterStatus.updateValues??>
    <#list topicOnClusterStatus.updateValues as updateValue>
        <#if updateValue?is_first><br/></#if>
        <@valueAssertDetail valueAssertion=updateValue/>
    </#list>
</#if>

<#import "violaton.ftl" as violatonUtil>

<#if topicOnClusterStatus.ruleViolations??>
    <#list topicOnClusterStatus.ruleViolations as ruleViolation>
        <@util.namedTypeStatusAlert type=ruleViolation.type alertInline=false/>
        <@violatonUtil.interpretMessage violation=ruleViolation.violation/>
        <hr/>
    </#list>
</#if>
<#if topicOnClusterStatus.currentConfigRuleViolations??>
    <#list topicOnClusterStatus.currentConfigRuleViolations as ruleViolation>
        <@util.namedTypeStatusAlert type=ruleViolation.type alertInline=false/>
        <@violatonUtil.interpretMessage violation=ruleViolation.violation/>
        <hr/>
    </#list>
</#if>
<#if topicOnClusterStatus.typeDescriptions?size gt 0>
    <#list topicOnClusterStatus.typeDescriptions as typeDescription>
        <@util.namedTypeStatusAlert type=typeDescription.type alertInline=false/>
        <@violatonUtil.richMessage message=typeDescription.message placeholders=typeDescription.placeholders/>
        <hr/>
    </#list>
</#if>
