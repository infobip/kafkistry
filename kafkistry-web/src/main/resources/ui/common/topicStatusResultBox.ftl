<#-- @ftlvariable name="statusType" type="com.infobip.kafkistry.service.topic.TopicInspectionResultType" -->
<#-- @ftlvariable name="asStatusFlag" type="java.lang.Boolean" -->
<#-- @ftlvariable name="alertInline" type="java.lang.Boolean" -->

<#import "util.ftl" as util>
<#import "infoIcon.ftl" as info>

<#assign isStatusFlag = asStatusFlag!false>
<#assign isAlertInline = alertInline!false>

<div class="text-nowrap alert
    <#if isAlertInline>alert-inline mb-1<#else>mb-0</#if>
    <#if isStatusFlag>status-flag</#if>
    ${util.levelToHtmlClass(statusType.level)}"
>
    ${statusType.name}
    <#if !isStatusFlag>
        <#assign tooltip>
            ${statusType.doc}
            <br/>
            <span>
                <strong>Issue category:</strong> <span class='text-info'>${statusType.category.name()}</span>
            </span>
        </#assign>
        <@info.icon tooltip=tooltip/>
    </#if>
</div>
