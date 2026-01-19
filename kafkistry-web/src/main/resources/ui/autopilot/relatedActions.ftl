<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="autopilotActions"  type="java.util.List<com.infobip.kafkistry.autopilot.repository.ActionFlow>" -->
<#-- @ftlvariable name="actionsSearchTerm" type="java.lang.String" -->
<#-- @ftlvariable name="maxActions" type="java.lang.Integer" -->

<#import "util.ftl" as autopilotUtil>

<#assign maxActions = maxActions!6>

<#if autopilotActions?size == 0>
    ----
<#else>
    <table class="table table-hover table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th>
                <a href="${appUrl.autopilot().showAutopilotPage()}#${actionsSearchTerm?url}"
                   class="btn btn-xsmall btn-outline-secondary">🔍</a>
            </th>
            <th>Action</th>
            <th>Target</th>
            <th>Time</th>
            <th>Result</th>
        </tr>
        </thead>
        <#list autopilotActions as actionFlow>
            <#if actionFlow?index gte maxActions >
                <tr>
                    <td colspan="100">
                        ...and ${autopilotActions?size - actionFlow?index} more action(s)
                        <a href="${appUrl.autopilot().showAutopilotPage()}#${actionsSearchTerm?url}"
                           class="btn btn-xsmall btn-outline-secondary">🔍</a>
                    </td>
                </tr>
                <#break>
            </#if>
            <#assign actionDescription = actionFlow.metadata.description>
            <#assign actionSearch = actionDescription.actionName?remove_ending("Action") + " " + actionDescription.targetType>
            <#list actionFlow.metadata.attributes as attrKey, attrVal>
                <#assign actionSearch = actionSearch + " " + attrVal>
            </#list>
            <tr>
                <td>
                    <a href="${appUrl.autopilot().showAutopilotPage()}#${actionSearch?url}"
                        class="btn btn-xsmall btn-outline-secondary">🔍</a>
                </td>
                <td>
                    <span class="font-monospace" title="${actionDescription.actionClass}">
                        ${actionDescription.actionName?remove_ending("Action")}
                    </span>
                </td>
                <td>
                    <#list actionFlow.metadata.attributes as attrKey, attrVal>
                        <span class="small badge bg-info" title="${attrKey}">${attrVal}</span>
                    </#list>
                </td>
                <td>
                    <span class="time small" data-time="${actionFlow.lastTimestamp?c}"></span>
                </td>
                <td>
                    <@autopilotUtil.outcomeBadge outcomeType=actionFlow.outcomeType/>
                </td>
            </tr>
        </#list>
    </table>
</#if>
