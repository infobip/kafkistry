<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIssues"  type="java.util.List<com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue>" -->

<#import "../common/util.ftl" as util>
<#import "../common/violaton.ftl" as violatonUtil>

<#if clusterIssues?size == 0>
    <span><i>No issues</i></span>
</#if>

<#list clusterIssues as issue>
    <div class="mb-2">
        <@util.namedTypeStatusAlert type=issue alertInline=false/>
        <@violatonUtil.interpretMessage violation=issue.violation/>
    </div>
</#list>
