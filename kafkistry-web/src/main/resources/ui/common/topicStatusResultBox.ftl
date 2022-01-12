<#-- @ftlvariable name="statusType" type="com.infobip.kafkistry.service.InspectionResultType" -->
<#-- @ftlvariable name="asStatusFlag" type="java.lang.Boolean" -->

<#import "util.ftl" as util>
<#import "documentation.ftl" as doc>
<#import "infoIcon.ftl" as info>

<#assign isStatusFlag = asStatusFlag!false>

<div class="text-nowrap alert <#if isStatusFlag>status-flag</#if> ${util.statusToHtmlClass(statusType)} mb-0" role="alert">
    ${statusType.toString()}
    <#if !isStatusFlag && (doc.topicStatus[statusType.name()])??>
        <#assign tooltip>
            ${doc.topicStatus[statusType.name()]}<br/>
            <span>
                <strong>Issue category:</strong> <span class='text-info'>${statusType.category.name()}</span>
            </span>
        </#assign>
        <@info.icon tooltip=tooltip/>
    </#if>
</div>
