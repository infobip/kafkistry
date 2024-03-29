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
                        <@util.namedTypeStatusAlert type=stateType alertInline=false/>
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
                        <@util.namedTypeStatusAlert type=issue alertInline=false/>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
