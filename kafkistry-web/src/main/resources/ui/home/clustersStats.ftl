<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clustersStats"  type="java.util.Map<com.infobip.kafkistry.kafkastate.StateType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if clustersStats?size == 0>
    <div class="p-3">
        <a class="btn btn-sm btn-primary" href="${appUrl.clusters().showAddCluster()}">
            Add cluster...
        </a>
    </div>
<#else>
    <table class="table table-sm m-0">
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
                <td>${count}</td>
            </tr>
        </#list>
    </table>
</#if>
