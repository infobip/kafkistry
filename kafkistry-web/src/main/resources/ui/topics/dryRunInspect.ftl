<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicStatuses" type="com.infobip.kafkistry.service.TopicStatuses" -->
<#-- @ftlvariable name="clustersResources" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.TopicDiskUsage>>" -->

<#import "../common/util.ftl" as util_>
<#import "topicResourceUsages.ftl" as usages>

<div class="border container rounded-lg p-1 alert-dark">

    <div class="card">
        <div class="card-header">
            <div class="row align-items-center">
                <div class="ml-2 col-">
                    <h4>Possible status over all clusters after saving: </h4>
                </div>
                <div class="m-1">
                    <#assign statusFlags = topicStatuses.aggStatusFlags>
                    <#assign clusterStatusFlags = util_.clusterStatusFlags(topicStatuses.statusPerClusters)>
                    <#include "../common/statusFlags.ftl">
                </div>
            </div>
        </div>

        <div class="card-body p-0">
            <table class="table table-sm table-bordered m-0">
                <thead class="thead-dark">
                <tr>
                    <th>Cluster</th>
                    <th>Status for config</th>
                </tr>
                </thead>
                <tbody>
                <#list topicStatuses.statusPerClusters as clusterStatus>
                    <tr>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(topicStatuses.topicName, clusterStatus.clusterIdentifier)}"
                                target="_blank">
                                <button class="btn btn-sm btn-outline-dark" title="Inspect this topic on this cluster...">
                                    ${clusterStatus.clusterIdentifier} 🔍
                                </button>
                            </a>
                        </td>
                        <td>
                            <#assign topicOnClusterStatus = clusterStatus.status>
                            <#include "../common/topicOnClusterStatus.ftl">
                        </td>
                    </tr>
                </#list>
                </tbody>
            </table>
        </div>
    </div>

    <div class="card">
        <div class="card-header">
            <h4>Expected metrics per clusters</h4>
        </div>
        <div class="card-body p-0">
            <table class="table table-sm table-bordered m-0">
                <thead class="thead-dark">
                <tr>
                    <th rowspan="2">Cluster</th>
                    <@usages.usageHeaderSectionCells/>
                </tr>
                <tr>
                    <@usages.usageHeaderCells/>
                </tr>
                </thead>
                <tbody>
                <#list topicStatuses.statusPerClusters as clusterStatus>
                    <tr>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(topicStatuses.topicName, clusterStatus.clusterIdentifier)}"
                               target="_blank">
                                <button class="btn btn-sm btn-outline-dark break-word" title="Inspect this topic on this cluster...">
                                    ${clusterStatus.clusterIdentifier} 🔍
                                </button>
                            </a>
                        </td>
                        <@usages.usageValuesCells optionalResourceRequiredUsages=clusterStatus.resourceRequiredUsages/>
                    </tr>
                </#list>
                </tbody>
            </table>
        </div>
    </div>

    <#list clustersResources as clusterIdentifier, optionalTopicResources>
        <div class="mt-1">
            <#if optionalTopicResources.value??>
                <#assign topicResources = optionalTopicResources.value>
                <#include "resources.ftl">
            <#else>
                <div class="alert alert-danger">
                    ${clusterIdentifier} -> ${optionalTopicResources.absentReason!'---'}
                </div>
            </#if>
        </div>
    </#list>

</div>