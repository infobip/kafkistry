<#-- @ftlvariable name="topicOnClusterStatus" type="com.infobip.kafkistry.service.topic.TopicOnClusterInspectionResult" -->

<#import "../common/util.ftl" as util>

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
<#list topicOnClusterStatus.typeDescriptions as typeDescription>
    <#assign detailedStatuses = detailedStatuses + [typeDescription.type]>
</#list>

<#list topicOnClusterStatus.types as statusType>
    <#if !(detailedStatuses?seq_contains(statusType))>
        <@util.namedTypeStatusAlert type=statusType/>
    </#if>
</#list>
<#assign alertInline = false>
<#if topicOnClusterStatus.wrongValues??>
    <#list topicOnClusterStatus.wrongValues as wrongValue>
        <#if wrongValue?is_first><br/></#if>
        <#assign statusType = wrongValue.type>
        <@util.namedTypeStatusAlert type=statusType alertInline=false/>
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
