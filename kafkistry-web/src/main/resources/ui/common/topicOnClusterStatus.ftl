<#-- @ftlvariable name="topicOnClusterStatus" type="com.infobip.kafkistry.service.topic.TopicOnClusterInspectionResult" -->

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

<#import "violaton.ftl" as violatonUtil>

<#if topicOnClusterStatus.ruleViolations??>
    <#list topicOnClusterStatus.ruleViolations as ruleViolation>
        <#assign statusType = ruleViolation.type><#include "topicStatusResultBox.ftl">
        <br/>
        <@violatonUtil.interpretMessage violation=ruleViolation.violation/>
        <hr/>
    </#list>
</#if>
<#if topicOnClusterStatus.currentConfigRuleViolations??>
    <#list topicOnClusterStatus.currentConfigRuleViolations as ruleViolation>
        <#assign statusType = ruleViolation.type><#include "topicStatusResultBox.ftl">
        <br/>
        <@violatonUtil.interpretMessage violation=ruleViolation.violation/>
        <hr/>
    </#list>
</#if>
