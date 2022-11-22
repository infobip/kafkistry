<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="autopilotStatus"  type="com.infobip.kafkistry.autopilot.service.AutopilotStatus" -->
<#-- @ftlvariable name="actionFlows"  type="java.util.List<com.infobip.kafkistry.autopilot.repository.ActionFlow>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Autopilot</title>
    <meta name="current-nav" content="nav-autopilot"/>
</head>

<body>

<script>
    $(document).ready(maybeFilterDatatableByUrlHash);
</script>


<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/violaton.ftl" as violationUtil>
<#import "util.ftl" as autopilotUtil>

<div class="container">

    <div class="card">
        <div class="card-header collapsed" data-target="#autopilot-info" data-toggle="collapsing">
            <h4>
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
                Autopilot info
            </h4>
        </div>
        <div class="card-body collapseable p-0" id="autopilot-info">
            <table class="table table-sm table-bordered">
                <thead class="thead-dark">
                <tr>
                    <th colspan="2" class="text-center">Properties</th>
                </tr>
                <tr>
                    <th>Property</th>
                    <th>Value</th>
                </tr>
                </thead>
                <tr>
                    <th>Cycle period every</th>
                    <td>${util.prettyDuration(autopilotStatus.cyclePeriodMs / 1000)}</td>
                </tr>
            </table>
            <table class="table table-sm table-bordered">
                <thead class="thead-dark">
                <tr>
                    <th colspan="2" class="text-center">Implemented capabilities</th>
                </tr>
                <tr>
                    <th>Action</th>
                    <th>Target Type</th>
                </tr>
                </thead>
                <#list autopilotStatus.possibleActions as actionDescription>
                    <tr>
                        <td>
                            <span class="text-monospace" title="${actionDescription.actionClass}">
                                ${actionDescription.actionName?remove_ending("Action")}
                            </span>
                            <@info.icon tooltip = actionDescription.doc/>
                        </td>
                        <td>
                            <span class="badge badge-primary"
                                  title="Action target type">${actionDescription.targetType}</span>
                        </td>
                    </tr>
                </#list>
            </table>
            <table class="table table-sm table-bordered">
                <#macro enablesDisabledValues what enabledValues disabledValues>
                <#-- @ftlvariable name="what" type="java.lang.String" -->
                <#-- @ftlvariable name="enabledValues" type="java.util.Collection<? extends java.lang.Object>" -->
                <#-- @ftlvariable name="disabledValues" type="java.util.Collection<? extends java.lang.Object>" -->
                    <#if enabledValues?size==0 && disabledValues?size==0>
                        <i>(all)</i>
                    </#if>
                    <#if enabledValues?size gt 0>
                        <strong class="text-success">Enabled ${what}</strong>:
                        <ul>
                            <#list enabledValues as value>
                                <li>
                                    <span class="text-monospace small">
                                        <#if value?is_string>${value}<#else>${value.toString()}</#if>
                                    </span>
                                </li>
                            </#list>
                        </ul>
                    </#if>
                    <#if disabledValues?size gt 0>
                        <strong class="text-danger">Disabled ${what}</strong>:
                        <ul>
                            <#list disabledValues as value>
                                <li>
                                    <span class="text-monospace small">
                                        <#if value?is_string>${value}<#else>${value.toString()}</#if>
                                    </span>
                                </li>
                            </#list>
                        </ul>
                    </#if>
                </#macro>
                <thead class="thead-dark">
                <tr>
                    <th colspan="2" class="text-center">Enabled actions filter</th>
                </tr>
                <tr>
                    <th>What</th>
                    <th>Status</th>
                </tr>
                </thead>
                <tr>
                    <th>Enabled globally</th>
                    <td>
                        <#if autopilotStatus.enableFilter.enabled>
                            <span class="badge badge-primary">ENABLED</span>
                        <#else>
                            <span class="badge badge-secondary">DISABLED</span>
                        </#if>
                    </td>
                </tr>
                <tr>
                    <th>Allowed binding implementations</th>
                    <td>
                        <@enablesDisabledValues
                            what = "binding classes"
                            enabledValues = autopilotStatus.enableFilter.bindingType.enabledClasses
                            disabledValues = autopilotStatus.enableFilter.bindingType.disabledClasses/>
                    </td>
                </tr>
                <tr>
                    <th>Allowed target type</th>
                    <td>
                        <@enablesDisabledValues
                            what = "target types"
                            enabledValues = autopilotStatus.enableFilter.targetType.enabledValues
                            disabledValues = autopilotStatus.enableFilter.targetType.disabledValues/>
                    </td>
                </tr>
                <tr>
                    <th>Allowed action type</th>
                    <td>
                        <@enablesDisabledValues
                            what = "action class types"
                            enabledValues = autopilotStatus.enableFilter.actionType.enabledClasses
                            disabledValues = autopilotStatus.enableFilter.actionType.disabledClasses/>
                    </td>
                </tr>
                <tr>
                    <th>Allowed cluster identifiers</th>
                    <td>
                        <@enablesDisabledValues
                            what = "cluster identfiers"
                            enabledValues = autopilotStatus.enableFilter.cluster.identifiers.included
                            disabledValues = autopilotStatus.enableFilter.cluster.identifiers.excluded/>
                    </td>
                </tr>
                <tr>
                    <th>Allowed cluster tags</th>
                    <td>
                        <@enablesDisabledValues
                            what = "cluster tags"
                            enabledValues = autopilotStatus.enableFilter.cluster.tags.included
                            disabledValues = autopilotStatus.enableFilter.cluster.tags.excluded/>
                    </td>
                </tr>
                <tr>
                    <th>Allowed action attributes</th>
                    <td>
                        <#if autopilotStatus.enableFilter.attribute?size==0>
                            <i>(all)</i>
                        </#if>
                        <#list autopilotStatus.enableFilter.attribute as attr, values>
                            <strong>Attribute</strong>: <code>${attr}</code><br/>
                            <@enablesDisabledValues
                                what = "values"
                                enabledValues = values.enabledValues
                                disabledValues = values.disabledValues/>
                            <#if !attr?is_last><hr/></#if>
                        </#list>
                    </td>
                </tr>
            </table>
        </div>
    </div>

    <div class="card">
        <div class="card-header">
            <h4>Actions</h4>
        </div>
        <div class="card-body pr-0 pl-0">
            <#assign datatableId = "autopilotActions">
            <#include "../common/loading.ftl">
            <table id="${datatableId}" class="table datatable" style="display: none;">
                <thead class="thead-dark">
                <tr>
                    <th class="no-sort-column details-toggle-column"></th>
                    <th>Action</th>
                    <th class="default-sort-dsc">Time</th>
                    <th>Outcome</th>
                    <th class="details-column">Flow</th>
                </tr>
                </thead>
                <#list actionFlows as actionFlow>
                    <#assign actionDescription = actionFlow.metadata.description>
                    <tr title="Action identifier: ${actionFlow.actionIdentifier}">
                        <td>
                            <span class="when-collapsed" title="expand...">▼</span>
                            <span class="when-not-collapsed" title="collapse...">△</span>
                        </td>
                        <td>
                            <span class="text-monospace" title="${actionDescription.actionClass}">
                                ${actionDescription.actionName?remove_ending("Action")}
                            </span>
                            <@info.icon tooltip = actionDescription.doc/>
                            <br/>
                            <#assign actionTargetUrl = autopilotUtil.resolveLink(actionFlow.metadata)>
                            <#if actionTargetUrl?length gt 0>
                                <a class="btn btn-xsmall btn-outline-dark"
                                   title="Open this ${actionDescription.targetType}"
                                   href="${actionTargetUrl}" target="_blank">🔍</a>
                            </#if>
                            <span class="small badge badge-primary"
                                  title="Action target type">${actionDescription.targetType}</span>
                            <#list actionFlow.metadata.attributes as attrKey, attrVal>
                                <span class="small badge badge-info" title="${attrKey}">${attrVal}</span>
                            </#list>
                        </td>
                        <td data-order="${actionFlow.lastTimestamp?c}">
                            <span class="time small" data-time="${actionFlow.lastTimestamp?c}"></span>
                        </td>
                        <td>
                            <@autopilotUtil.outcomeBadge outcomeType=actionFlow.outcomeType/>
                            <#if actionFlow.flow?size gt 1>
                                (+${actionFlow.flow?size - 1})
                            </#if>
                        </td>
                        <td>
                            <table class="table table-sm m-0 no-hover">
                                <thead class="thead-light">
                                <tr>
                                    <th>Run time</th>
                                    <th>Autopilot</th>
                                    <th>Outcome</th>
                                    <th>Problems</th>
                                </tr>
                                </thead>
                                <#list actionFlow.flow?reverse as outcomeItem>
                                    <tr>
                                        <td class="small time" data-time="${outcomeItem.timestamp?c}"></td>
                                        <td>
                                            <code>${outcomeItem.sourceAutopilot}</code></td>
                                        <td>
                                            <@autopilotUtil.outcomeBadge outcomeType=outcomeItem.type/>
                                        </td>
                                        <td>
                                            <#if outcomeItem.unstable?size gt 0>
                                                <div class="alert alert-warning">
                                                    <strong>Unstable cluster</strong>:<br/>
                                                    <table class="table table-sm">
                                                        <#list outcomeItem.unstable as unstable>
                                                            <tr>
                                                                <td title="Name of state provider/scraper">
                                                                    <code>${unstable.stateTypeName}</code>
                                                                </td>
                                                                <td title="Last seen issue time, status: ${unstable.stateType.name()}">
                                                                    <span class="small time" data-time="${unstable.timestamp?c}"></span>
                                                                </td>
                                                            </tr>
                                                        </#list>
                                                    </table>
                                                </div>
                                            </#if>
                                            <#list outcomeItem.blockers as blocker>
                                                <div class="alert alert-warning">
                                                    <strong>Blocker</strong>:<br/>
                                                    <@violationUtil.richMessage
                                                    message=blocker.message placeholders=blocker.placeholders/>
                                                </div>
                                            </#list>
                                            <#if outcomeItem.executionError??>
                                                <div class="alert alert-danger">
                                                    <strong>Execution error</strong>:
                                                    <pre class="pre-message">${outcomeItem.executionError}</pre>
                                                </div>
                                            </#if>
                                            <#if outcomeItem.unstable?size == 0 && outcomeItem.blockers?size == 0 && !(outcomeItem.executionError??)>
                                                ---
                                            </#if>
                                        </td>
                                    </tr>
                                </#list>
                            </table>
                        </td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
