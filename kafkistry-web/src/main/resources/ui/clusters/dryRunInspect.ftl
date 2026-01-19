<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="clusterDryRunInspect" type="com.infobip.kafkistry.service.cluster.ClusterDryRunInspect" -->

<#import "../common/util.ftl" as util>
<#import "../quotas/util.ftl" as quotaUtil>

<#if clusterDryRunInspect.errors?size gt 0>
    <div class="card">
        <div class="card-header">
            <h4><span class="badge bg-danger">WARNING</span> Having ${clusterDryRunInspect.errors?size} errors</h4>
        </div>
        <div class="card-body">
            <#list clusterDryRunInspect.errors as error>
                <div class="alert alert-danger">
                    ${error}
                </div>
            </#list>
        </div>
    </div>
    <br/>
</#if>

<div class="card">
    <div class="card-header">
        <h4>Current disk resource usage</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = clusterDryRunInspect.clusterDiskUsageBefore>
        <#include "resourcesInspect.ftl">
    </div>
</div>


<div class="card">
    <div class="card-header">
        <h4>Effective disk resource usage</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = clusterDryRunInspect.clusterDiskUsageAfter>
        <#include "resourcesInspect.ftl">
    </div>
</div>

<div class="card">
    <div class="card-header">
        <h4>Diff disk resource usage (effective vs. current)</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = clusterDryRunInspect.clusterDiskUsageDiff>
        <#assign diffModeEnabled = true>
        <#include "resourcesInspect.ftl">
        <#assign diffModeEnabled = false>
    </div>
</div>

<#macro showDiff diff stateClass>
    <#switch stateClass>
        <#case "alert-danger">
        <#case "alert-warnig">
            <#assign sentiment = "NEGATIVE">
            <#break>
        <#case "alert-success">
            <#assign sentiment = "POSITIVE">
            <#break>
        <#default>
            <#assign sentiment = "NEUTRAL">
    </#switch>
    <#assign textClass = "">
    <#if sentiment == "NEGATIVE" && (diff gt 0)>
        <#assign textClass = "text-danger">
    <#elseif sentiment == "NEGATIVE" && (diff lt 0)>
        <#assign textClass = "text-success">
    <#elseif sentiment == "POSITIVE" && (diff gt 0)>
        <#assign textClass = "text-success">
    <#elseif sentiment == "POSITIVE" && (diff lt 0)>
        <#assign textClass = "text-danger">
    <#else>
        <#if diff gt 0>
            <#assign textClass = "text-primary">
        <#else>
            <#assign textClass = "text-info">
        </#if>
    </#if>

    <#if diff gt 0>
        <span class="${textClass} font-weight-bold">+${diff}</span>
    <#elseif diff lt 0>
        <span class="${textClass} font-weight-bold">${diff}</span>
    <#else>
        ---
    </#if>
</#macro>

<br/>

<#macro topicsList topics title topicDisks>
<#-- @ftlvariable name="topicDisks" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.OptionalValue<com.infobip.kafkistry.service.resources.TopicClusterDiskUsage>>" -->
<div class="card">
    <div class="card-header collapsed" data-bs-target=".diff-topics-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
        <div class="h5">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="message">${topics?size} ${title}...</span>
        </div>
    </div>
    <div class="card-body p-0 pt-2 pb-2 diff-topics-${title?replace('[^A-Za-z]', '_', 'r')} collapseable">
        <table class="table table-hover table-bordered datatable">
            <thead class="thead-light">
            <tr>
                <th>Topic</th>
                <th>Replica count diff</th>
                <th>Actual usage diff</th>
                <th>Possible usage diff</th>
                <th>Unbounded usage diff</th>
            </tr>
            </thead>
            <#list topics as topic>
                <tr>
                    <td><a target="_blank" href="${appUrl.topics().showTopic(topic)}">${topic}</a></td>
                    <#if (topicDisks[topic].value)??>
                        <#assign topicDisk = topicDisks[topic].value>
                        <#assign diffModeEnabled = true>
                        <td data-order="${topicDisk.combined.replicasCount}">
                            <span class="${signClass(topicDisk.combined.replicasCount)}">
                                ${util.numberToString(topicDisk.combined.replicasCount, true)}
                            </span>
                        </td>
                        <td data-order="${topicDisk.combined.actualUsedBytes!''}">
                            <#if topicDisk.combined.actualUsedBytes??>
                                <span class="${signClass(topicDisk.combined.actualUsedBytes)}">
                                    ${util.prettyDataSize(topicDisk.combined.actualUsedBytes, true)}
                                </span>
                            <#else>
                                ---
                            </#if>
                        </td>
                        <td data-order="${topicDisk.combined.retentionBoundedBytes!''}">
                            <#if topicDisk.combined.retentionBoundedBytes??>
                                <span class="${signClass(topicDisk.combined.retentionBoundedBytes)}">
                                    ${util.prettyDataSize(topicDisk.combined.retentionBoundedBytes, true)}
                                </span>
                            <#else>
                                ---
                            </#if>
                        </td>
                        <td data-order="${topicDisk.combined.unboundedUsageBytes!''}">
                            <#if topicDisk.combined.unboundedUsageBytes??>
                                <span class="${signClass(topicDisk.combined.unboundedUsageBytes)}">
                                    ${util.prettyDataSize(topicDisk.combined.unboundedUsageBytes, true)}
                                </span>
                            <#else>
                                ---
                            </#if>
                        </td>
                        <#assign diffModeEnabled = false>
                    <#else>
                        <td colspan="4">
                            <#if (topicDisks[topic].absentReason)??>
                                <div class="alert alert-danger">${topicDisks[topic].absentReason}</div>
                            <#else>
                                ---
                            </#if>
                        </td>
                        <td style="display: none;"></td> <!-- datatable wants to have equal number of columns per each row -->
                        <td style="display: none;"></td>
                        <td style="display: none;"></td>
                    </#if>
                </tr>
            </#list>
        </table>
    </div>
</div>
</#macro>

<#macro aclsList acls title>
<div class="card">
    <div class="card-header collapsed" data-bs-target=".diff-acls-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
        <div class="h5">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="message">${acls?size} ${title}...</span>
        </div>
    </div>
    <div class="card-body diff-acls-${title?replace('[^A-Za-z]', '_', 'r')} collapseable">
        <ul>
        <#list acls as acl>
            <li>
                <a target="_blank" href="${appUrl.acls().showAllPrincipalAclsRule(acl.principal, acl.toString())}">
                    <code>${acl.toString()}</code>
                </a>
            </li>
        </#list>
        </ul>
    </div>
</div>
</#macro>

<#macro quotasList quotas title>
<div class="card">
    <div class="card-header collapsed" data-bs-target=".diff-acls-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
        <div class="h5">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="message">${quotas?size} ${title}...</span>
        </div>
    </div>
    <div class="card-body diff-acls-${title?replace('[^A-Za-z]', '_', 'r')} collapseable">
        <ul>
        <#list quotas as entity>
            <li>
                <a target="_blank" href="${appUrl.quotas().showEntity(entity.asID())}">
                    <@quotaUtil.entity entity = entity/>
                </a>
            </li>
        </#list>
        </ul>
    </div>
</div>
</#macro>

<#assign topicsDiff = clusterDryRunInspect.topicsDiff>
<div class="card">
    <div class="card-header">
        <h4>Topics diff</h4>
    </div>
    <div class="card-body p-0">
        <#if topicsDiff.problems?size gt 0>
            <div class="card">
                <div class="card-header">
                    <h4><span class="badge bg-danger">WARNING</span> Having ${topicsDiff.problems?size} problems</h4>
                </div>
                <div class="card-body">
                    <#list topicsDiff.problems as problem>
                        <div class="alert alert-danger">
                            ${problem}
                        </div>
                    </#list>
                </div>
            </div>
            <br/>
        </#if>
        <table class="table table-hover table-sm">
            <thead class="table-theme-dark">
            <tr>
                <th>Topic status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list topicsDiff.statusCounts as stateTypeCountDiff>
                <#assign stateType = stateTypeCountDiff.type>
                <#assign countDiff = stateTypeCountDiff.quantity>
                <tr>
                    <td>
                        <#assign stateClass = util.levelToHtmlClass(stateType.level)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType.name}
                        </div>
                    </td>
                    <td>${countDiff.before}</td>
                    <td>${countDiff.after}</td>
                    <td><@showDiff diff = countDiff.diff stateClass = stateClass/></td>
                </tr>
            </#list>
        </table>
        <#if topicsDiff.topicsToCreate?size gt 0>
            <@topicsList topics=topicsDiff.topicsToCreate title="topics to create" topicDisks=topicsDiff.topicDiskUsages/>
        </#if>
        <#if topicsDiff.topicsToDelete?size gt 0>
            <@topicsList topics=clusterDryRunInspect.topicsDiff.topicsToDelete title="topics to delete" topicDisks=topicsDiff.topicDiskUsages/>
        </#if>
        <#if topicsDiff.topicsToReconfigure?size gt 0>
            <@topicsList topics=topicsDiff.topicsToReconfigure title="topics to re-configure" topicDisks=topicsDiff.topicDiskUsages/>
        </#if>
        <#if clusterDryRunInspect.topicsDiff.topicsToReScale?size gt 0>
            <@topicsList topics=topicsDiff.topicsToReScale title="topics to re-scale" topicDisks=topicsDiff.topicDiskUsages/>
        </#if>
    </div>
</div>

<br/>

<div class="card">
    <div class="card-header">
        <h4>ACLs diff</h4>
    </div>
    <div class="card-body p-0">
        <table class="table table-hover table-sm">
            <thead class="table-theme-dark">
            <tr>
                <th>ACL status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list clusterDryRunInspect.aclsDiff.statusCounts as stateTypeCountDiff>
                <#assign stateType = stateTypeCountDiff.type>
                <#assign countDiff = stateTypeCountDiff.quantity>
                <tr>
                    <td>
                        <#assign stateClass = util.levelToHtmlClass(stateType.level)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType.name}
                        </div>
                    </td>
                    <td>${countDiff.before}</td>
                    <td>${countDiff.after}</td>
                    <td><@showDiff diff = countDiff.diff stateClass = stateClass/></td>
                </tr>
            </#list>
        </table>
        <#if clusterDryRunInspect.aclsDiff.aclsToCreate?size gt 0>
            <@aclsList acls=clusterDryRunInspect.aclsDiff.aclsToCreate title="ACLs to create"/>
        </#if>
        <#if clusterDryRunInspect.aclsDiff.aclsToDelete?size gt 0>
            <@aclsList acls=clusterDryRunInspect.aclsDiff.aclsToDelete title="ACLs to delete"/>
        </#if>
    </div>
</div>

<br/>

<div class="card">
    <div class="card-header">
        <h4>Quotas diff</h4>
    </div>
    <div class="card-body p-0">
        <table class="table table-hover table-sm">
            <thead class="table-theme-dark">
            <tr>
                <th>Entity quotas status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list clusterDryRunInspect.quotasDiff.statusCounts as stateTypeCountDiff>
                <#assign stateType = stateTypeCountDiff.type>
                <#assign countDiff = stateTypeCountDiff.quantity>
                <tr>
                    <td>
                        <#assign stateClass = util.levelToHtmlClass(stateType.level)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType.name}
                        </div>
                    </td>
                    <td>${countDiff.before}</td>
                    <td>${countDiff.after}</td>
                    <td><@showDiff diff = countDiff.diff stateClass = stateClass/></td>
                </tr>
            </#list>
        </table>
        <#if clusterDryRunInspect.quotasDiff.quotasToCreate?size gt 0>
            <@quotasList quotas=clusterDryRunInspect.quotasDiff.quotasToCreate title="quotas to create"/>
        </#if>
        <#if clusterDryRunInspect.quotasDiff.quotasToDelete?size gt 0>
            <@quotasList quotas=clusterDryRunInspect.quotasDiff.quotasToDelete title="quotas to delete"/>
        </#if>
        <#if clusterDryRunInspect.quotasDiff.quotasToReconfigure?size gt 0>
            <@quotasList quotas=clusterDryRunInspect.quotasDiff.quotasToReconfigure title="quotas to re-configure"/>
        </#if>
    </div>
</div>

<div class="inspect-summary" style="display: none;">
    <#if clusterDryRunInspect.errors?size gt 0>
        <span class="badge alert-danger">${clusterDryRunInspect.errors?size} ERROR(s)</span>
    </#if>
    <span class="badge ${util.usageLevelToHtmlClass(clusterDryRunInspect.clusterDiskUsageAfter.worstPossibleUsageLevel)}"
          title="Possible disk usage level">
        Possible usage: ${clusterDryRunInspect.clusterDiskUsageAfter.worstPossibleUsageLevel}
    </span>

    <#if topicsDiff.problems?size gt 0>
        <span class="badge alert-danger">${topicsDiff.problems?size} Topic problem(s)</span>
    </#if>

    <#assign gearSymbol>&#9881;</#assign>

    <#if topicsDiff.affectedTopicsCount gt 0>
        <span class="badge bg-neutral" title="Affected topics count">
            ${gearSymbol} Topics: ${topicsDiff.affectedTopicsCount}
        </span>
    </#if>

    <#assign affectedAclsCount = clusterDryRunInspect.aclsDiff.aclsToCreate?size
        + clusterDryRunInspect.aclsDiff.aclsToDelete?size
    >
    <#if affectedAclsCount gt 0>
        <span class="badge bg-neutral" title="Affected ACLs count">
            ${gearSymbol} ACLs: ${affectedAclsCount}
        </span>
    </#if>

    <#assign affectedQuotasCount = clusterDryRunInspect.quotasDiff.quotasToCreate?size
        + clusterDryRunInspect.quotasDiff.quotasToDelete?size
        + clusterDryRunInspect.quotasDiff.quotasToReconfigure?size
    >
    <#if affectedQuotasCount gt 0>
        <span class="badge bg-neutral" title="Affected Client quotas count">
            ${gearSymbol} Quotas: ${affectedQuotasCount}
        </span>
    </#if>

</div>


