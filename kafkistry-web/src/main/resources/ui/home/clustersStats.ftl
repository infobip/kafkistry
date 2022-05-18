<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clustersStats"  type="java.util.Map<com.infobip.kafkistry.kafkastate.StateType, java.lang.Integer>" -->
<#-- @ftlvariable name="issuesStats"  type="java.util.Map<com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue, java.lang.Integer>" -->
<#-- @ftlvariable name="tagsCounts"  type="java.util.Map<java.lang.String, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>
<#import "../common/violaton.ftl" as violatonUtil>

<#if clustersStats?size == 0>
    <div class="p-3">
        <a class="btn btn-sm btn-primary" href="${appUrl.clusters().showAddCluster()}">
            Add cluster...
        </a>
    </div>
<#else>
    <table class="table table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th colspan="2">State</th>
        </tr>
        </thead>
        <#list clustersStats as stateType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.clusters().showClusters()}#${stateType}" title="Click to filter clusters...">
                        <#assign stateClass = util.clusterStatusToHtmlClass(stateType)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType}
                        </div>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
    <table class="table table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th colspan="2">Issues</th>
        </tr>
        </thead>
        <#list issuesStats as issue, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.clusters().showClusters()}#${issue.name}" title="Click to filter clusters...">
                        <#assign alertClass = violatonUtil.severityClass(issue.violation.severity)?replace("badge", "alert")>
                        <div class="alert alert-sm ${alertClass} mb-0">
                            ${issue.name}
                        </div>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
    <table class="table table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th colspan="2">Tags</th>
        </tr>
        </thead>
        <#list tagsCounts as tag, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.clusters().showClusters()}#${tag}" title="Click to filter clusters...">
                        <span class="badge badge-secondary">${tag}</span>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
