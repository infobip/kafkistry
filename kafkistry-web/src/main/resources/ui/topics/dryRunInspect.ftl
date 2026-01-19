<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicStatuses" type="com.infobip.kafkistry.service.topic.TopicStatuses" -->
<#-- @ftlvariable name="clustersResources" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.TopicDiskUsage>>" -->
<#-- @ftlvariable name="blockers" type="java.util.List<java.lang.String>" -->

<#import "../common/util.ftl" as util_>
<#import "topicResourceUsages.ftl" as usages>

<div class="border container rounded-lg p-1 alert-dark">

    <div class="card">
        <div class="card-header">
            <div class="row align-items-center">
                <div class="ml-2 col-auto">
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
            <table class="table table-hover table-sm table-bordered m-0">
                <thead class="table-theme-dark">
                <tr>
                    <th>Topic @ Cluster</th>
                    <th>Status for config</th>
                </tr>
                </thead>
                <tbody>
                <#list topicStatuses.statusPerClusters as clusterStatus>
                    <tr>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(topicStatuses.topicName, clusterStatus.clusterIdentifier)}"
                                target="_blank" title="Inspect this topic on this cluster..." class="text-nowrap">
                                    ${clusterStatus.clusterIdentifier}
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
        <div class="card-header collapsed" data-bs-target=".expected-metrics-body" data-toggle="collapsing">
            <h4>
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
                Expected metrics per clusters
            </h4>
        </div>
        <div class="card-body p-0 expected-metrics-body collapseable">
            <table class="table table-hover table-sm table-bordered m-0">
                <thead class="table-theme-dark">
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
                               target="_blank" title="Inspect this topic on this cluster..." class="text-nowrap">
                                    ${clusterStatus.clusterIdentifier}
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

    <div class="card">
        <div class="card-header">
            <h4>Blocker issues</h4>
        </div>
        <div class="card-body p-0">
            <#if blockers?size == 0>
                <div class="alert alert-success">
                    <span class="badge bg-success">NONE</span>
                </div>
            <#else>
                <div class="alert alert-danger">
                    <strong>There are ${blockers?size} issues:</strong>
                    <ul>
                        <#list blockers as blocker>
                            <li class="blocker-issue-item">${blocker}</li>
                        </#list>
                    </ul>
                    <label class="bg-warning-subtle rounded border px-3 py-1">
                        <input name="blockers-consent" type="checkbox">
                        Allow saving ignoring blockers
                    </label>
                </div>
            </#if>
        </div>
    </div>

</div>