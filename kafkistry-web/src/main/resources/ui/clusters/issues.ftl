<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIssues"  type="java.util.List<com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue>" -->

<#import "../common/violaton.ftl" as violatonUtil>

<#if clusterIssues?size == 0>
    <span><i>No issues</i></span>
</#if>

<#list clusterIssues as issue>
    <div class="mb-2">
        <#assign alertClass = violatonUtil.severityClass(issue.violation.severity)?replace("badge", "alert")>
        <div class="alert ${alertClass} mb-0">
            ${issue.name}
        </div>
        <@violatonUtil.interpretMessage violation=issue.violation/>
    </div>
</#list>
