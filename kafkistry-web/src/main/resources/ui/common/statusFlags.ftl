<#-- @ftlvariable name="statusFlags" type="com.infobip.kafkistry.service.topic.StatusFlags" -->
<#-- @ftlvariable name="clusterStatusFlags" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.StatusFlags>" -->
<#-- @ftlvariable name="allTopicStatusTypes" type="java.util.List<com.infobip.kafkistry.service.topic.InspectionResultType>" -->
<#-- @ftlvariable name="nonOkTopicStatusTypes" type="java.util.List<com.infobip.kafkistry.service.topic.InspectionResultType>" -->

<#import "infoIcon.ftl" as info>
<#import "util.ftl" as util>
<#import "documentation.ftl" as doc>

<div style="display: none;">
    <#list allTopicStatusTypes![] as statusType>
        ${statusType}
    </#list>
</div>

<#if statusFlags.allOk>
    <div role="alert" class="status-flag alert alert-success mb-0">
        ALL OK
    </div>
<#elseif nonOkTopicStatusTypes?? && nonOkTopicStatusTypes?size <= 2>
    <#list nonOkTopicStatusTypes as statusType>
        <#include "topicStatusResultBox.ftl">
    </#list>
<#else>
    <#if !statusFlags.configOk>
        <div role="alert" class="status-flag alert alert-danger mb-0">
            CONFIG MISMATCH
        </div>
    </#if>
    <#if !statusFlags.visibleOk>
        <div role="alert" class="status-flag alert alert-danger mb-0">
            UNREACHABLE
        </div>
    </#if>
    <#if !statusFlags.ruleCheckOk>
        <div role="alert" class="status-flag alert alert-warning mb-0">
            RULE VIOLATED
        </div>
    </#if>
    <#if !statusFlags.runtimeOk>
        <div role="alert" class="status-flag alert alert-warning mb-0">
            RUNTIME ISSUE
        </div>
    </#if>
</#if>