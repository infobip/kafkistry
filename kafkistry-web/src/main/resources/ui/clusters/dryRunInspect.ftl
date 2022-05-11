<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="clusterDryRunInspect" type="com.infobip.kafkistry.service.cluster.ClusterDryRunInspect" -->

<#import "../common/util.ftl" as util>
<#import "../quotas/util.ftl" as quotaUtil>

<#if clusterDryRunInspect.errors?size gt 0>
    <div class="card">
        <div class="card-header">
            <h4><span class="badge badge-danger">WARNING</span> Having ${clusterDryRunInspect.errors?size} errors</h4>
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

<#macro topicsList topics title>
<div class="card">
    <div class="card-header collapsed" data-target=".diff-topics-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
        <div class="h5">
            <span class="when-collapsed" title="expand...">▼</span>
            <span class="when-not-collapsed" title="collapse...">△</span>
            <span class="message">${topics?size} ${title}...</span>
        </div>
    </div>
    <div class="card-body diff-topics-${title?replace('[^A-Za-z]', '_', 'r')} collapseable">
        <ul>
        <#list topics as topic>
            <li>
                <a target="_blank" href="${appUrl.topics().showTopic(topic)}">${topic}</a>
            </li>
        </#list>
        </ul>
    </div>
</div>
</#macro>

<#macro aclsList acls title>
<div class="card">
    <div class="card-header collapsed" data-target=".diff-acls-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
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
    <div class="card-header collapsed" data-target=".diff-acls-${title?replace('[^A-Za-z]', '_', 'r')}" data-toggle="collapsing">
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

<div class="card">
    <div class="card-header">
        <h4>Topics diff</h4>
    </div>
    <div class="card-body p-0">
        <table class="table table-sm">
            <thead class="thead-dark">
            <tr>
                <th>Topic status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list clusterDryRunInspect.topicsDiff.statusCounts as stateType, countDiff>
                <tr>
                    <td>
                        <#assign stateClass = util.statusToHtmlClass(stateType)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType}
                        </div>
                    </td>
                    <td>${countDiff.before}</td>
                    <td>${countDiff.after}</td>
                    <td><@showDiff diff = countDiff.diff stateClass = stateClass/></td>
                </tr>
            </#list>
        </table>
        <#if clusterDryRunInspect.topicsDiff.topicsToCreate?size gt 0>
            <@topicsList topics=clusterDryRunInspect.topicsDiff.topicsToCreate title="topics to create"/>
        </#if>
        <#if clusterDryRunInspect.topicsDiff.topicsToDelete?size gt 0>
            <@topicsList topics=clusterDryRunInspect.topicsDiff.topicsToDelete title="topics to delete"/>
        </#if>
        <#if clusterDryRunInspect.topicsDiff.topicsToReconfigure?size gt 0>
            <@topicsList topics=clusterDryRunInspect.topicsDiff.topicsToReconfigure title="topics to re-configure"/>
        </#if>
        <#if clusterDryRunInspect.topicsDiff.topicsToReScale?size gt 0>
            <@topicsList topics=clusterDryRunInspect.topicsDiff.topicsToReScale title="topics to re-scale"/>
        </#if>
    </div>
</div>

<br/>

<div class="card">
    <div class="card-header">
        <h4>ACLs diff</h4>
    </div>
    <div class="card-body p-0">
        <table class="table table-sm">
            <thead class="thead-dark">
            <tr>
                <th>ACL status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list clusterDryRunInspect.aclsDiff.statusCounts as stateType, countDiff>
                <tr>
                    <td>
                        <#assign stateClass = util.statusTypeAlertClass(stateType)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType}
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
        <table class="table table-sm">
            <thead class="thead-dark">
            <tr>
                <th>Entity quotas status type</th>
                <th>Current count</th>
                <th>Count after</th>
                <th>Diff</th>
            </tr>
            </thead>
            <#list clusterDryRunInspect.quotasDiff.statusCounts as stateType, countDiff>
                <tr>
                    <td>
                        <#assign stateClass = util.statusTypeAlertClass(stateType)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType}
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
