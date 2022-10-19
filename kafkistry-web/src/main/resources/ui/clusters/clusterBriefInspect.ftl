<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIssues"  type="java.util.List<com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue>" -->
<#-- @ftlvariable name="clusterTopics"  type="com.infobip.kafkistry.service.topic.ClusterTopicsStatuses" -->
<#-- @ftlvariable name="clusterAcls"  type="com.infobip.kafkistry.service.acl.ClusterAclsInspection" -->
<#-- @ftlvariable name="clusterQuotas"  type="com.infobip.kafkistry.service.quotas.ClusterQuotasInspection" -->
<#-- @ftlvariable name="clusterGroups"  type="com.infobip.kafkistry.service.consumers.ClusterConsumerGroups" -->

<#import "../common/infoIcon.ftl" as info>
<#import "../common/violaton.ftl" as violatonUtil>
<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumersUtil>

<#assign fullyOk = true>
<#assign clusterUrl = appUrl.clusters().showCluster(clusterIdentifier)>

<#function mostSevereClass alertClasses>
    <#if alertClasses?size == 0>
        <#return "">
    </#if>
    <#assign alertSeverities = {
    "alert-info": 0,
    "alert-secondary": 1,
    "alert-primary": 2,
    "alert-success": 3,
    "alert-warning": 4,
    "alert-danger": 5,
    "bg-danger text-white": 6
    }>
    <#assign worst = alertClasses[0]>
    <#list alertClasses as alertClass>
        <#assign severtyScore = (alertSeverities[alertClass])!-1>
        <#assign worstSevertyScore = (alertSeverities[worst])!-1>
        <#if severtyScore gt worstSevertyScore>
            <#assign worst = alertClass>
        </#if>
    </#list>
    <#return worst>
</#function>

<#---- Cluster issues ---->

<#if clusterIssues?size gt 0>
    <#assign fullyOk = false>
    <#assign alerts = []>
    <#assign seenIssues = []>
    <#assign issuesTooltip>
        <table class="table table-sm table-borderless ml-1">
        <#list clusterIssues as issue>
            <#if seenIssues?seq_contains(issue.name)>
                <#continue>
            </#if>
            <#assign alerts = alerts + [util.levelToHtmlClass(issue.level)]>
            <tr>
                <td class="p-0">
                    <@util.namedTypeStatusAlert type=issue alertInline=false small=true/>
                </td>
            </tr>
            <#assign seenIssues = seenIssues + [issue.name]>
        </#list>
        </table>
    </#assign>
    <div class="text-nowrap alert alert-sm ${mostSevereClass(alerts)} mb-1 collapsed" data-toggle="collapsing"
         data-target="#cluster-issues-${clusterIdentifier}">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        CLUSTER ISSUES
    </div>
    <div id="cluster-issues-${clusterIdentifier}" class="collapseable pb-2">
        ${issuesTooltip}
    </div>
</#if>


<#---- Topics ---->

<#if !clusterTopics.aggStatusFlags.allOk>
    <#assign fullyOk = false>
    <#assign alerts = []>
    <#assign topicsCountsTooltip>
        <table class="table table-sm table-borderless ml-1">
            <#list clusterTopics.topicsStatusCounts as statusTypeCount>
                <#assign statusType = statusTypeCount.type>
                <#assign count = statusTypeCount.quantity>
                <tr>
                    <td class="p-0">
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${clusterUrl}#topics|${statusType.name}" title="Filter topics by...">
                            <#assign alerts = alerts + [util.levelToHtmlClass(statusType.level)]>
                            <@util.namedTypeStatusAlert type=statusType alertInline=false small=true/>
                        </a>
                    </td>
                    <td>${count}</td>
                </tr>
            </#list>
        </table>
    </#assign>
    <div class="text-nowrap alert alert-sm ${mostSevereClass(alerts)} mb-1 collapsed"
         data-toggle="collapsing" data-target="#topic-issues-${clusterIdentifier}">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        TOPIC ISSUES
    </div>
    <div id="topic-issues-${clusterIdentifier}" class="collapseable pb-2">
        ${topicsCountsTooltip}
    </div>
</#if>


<#---- ACLs ---->

<#if !clusterAcls.status.ok>
    <#assign fullyOk = false>
    <#assign alerts = []>
    <#assign aclsCountsTooltip>
        <table class="table table-sm table-borderless ml-1">
            <#list clusterAcls.status.statusCounts as statusTypeCount>
                <#assign statusType = statusTypeCount.type>
                <#assign count = statusTypeCount.quantity>
                <tr>
                    <td class="p-0">
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${clusterUrl}#acls|${statusType.name}" title="Filter ACLs by...">
                            <#assign alerts = alerts + [util.levelToHtmlClass(statusType.level)]>
                            <@util.namedTypeStatusAlert type=statusType alertInline=false small=true/>
                        </a>
                    </td>
                    <td>${count}</td>
                </tr>
            </#list>
        </table>
    </#assign>
    <div class="text-nowrap alert alert-sm ${mostSevereClass(alerts)} mb-1 collapsed"
         data-toggle="collapsing" data-target="#acls-issues-${clusterIdentifier}">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        ACLS ISSUES
    </div>
    <div id="acls-issues-${clusterIdentifier}" class="collapseable pb-2">
        ${aclsCountsTooltip}
    </div>
</#if>


<#---- Quotas ---->

<#if !clusterQuotas.status.ok>
    <#assign fullyOk = false>
    <#assign alerts = []>
    <#assign quotasCountsTooltip>
        <table class="table table-sm table-borderless ml-1">
            <#list clusterQuotas.status.statusCounts as statusTypeCount>
                <#assign statusType = statusTypeCount.type>
                <#assign count = statusTypeCount.quantity>
                <tr>
                    <td class="p-0">
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${clusterUrl}#quotas|${statusType.name}" title="Filter quotas by...">
                            <#assign alerts = alerts + [util.levelToHtmlClass(statusType.level)]>
                            <@util.namedTypeStatusAlert type=statusType alertInline=false small=true/>
                        </a>
                    </td>
                    <td>${count}</td>
                </tr>
            </#list>
        </table>
    </#assign>
    <div class="text-nowrap alert alert-sm ${mostSevereClass(alerts)} mb-1 collapsed"
         data-toggle="collapsing" data-target="#quotas-issues-${clusterIdentifier}">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        QUOTAS ISSUES
    </div>
    <div id="quotas-issues-${clusterIdentifier}" class="collapseable pb-2">
        ${quotasCountsTooltip}
    </div>
</#if>


<#---- Consumer groups ---->

<#assign cgFullyOk = true>
<#assign alerts = []>
<#assign consumerGroupsCountsTooltip>
    <table class="table table-sm table-borderless ml-1">
        <#list clusterGroups.consumersStats.lagStatusCounts as lagStatusType, count>
            <#if lagStatusType.toString() != "NO_LAG" && lagStatusType.toString() != "MINOR_LAG">
                <#assign cgFullyOk = false>
            </#if>
            <tr>
                <td class="p-0">
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${clusterUrl}#consumer-groups|${lagStatusType}" title="Filter consumer groups by...">
                        <#assign alerts = alerts + [util.levelToHtmlClass(lagStatusType.level)]>
                        <@util.namedTypeStatusAlert type=lagStatusType alertInline=false small=true/>
                    </a>
                </td>
                <td>${count}</td>
            </tr>
        </#list>
        <#list clusterGroups.consumersStats.consumerStatusCounts as stateStatusType, count>
            <#if stateStatusType.toString() != "STABLE">
                <#assign cgFullyOk = false>
            </#if>
            <tr>
                <td class="p-0">
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${clusterUrl}#consumer-groups|${stateStatusType}" title="Filter consumer groups by...">
                        <#assign alerts = alerts + [util.levelToHtmlClass(stateStatusType.level)]>
                        <@util.namedTypeStatusAlert type=stateStatusType alertInline=false small=true/>
                    </a>
                </td>
                <td>${count}</td>
            </tr>
        </#list>
    </table>
</#assign>
<#if !cgFullyOk>
    <#assign fullyOk = false>
    <div class="text-nowrap alert alert-sm ${mostSevereClass(alerts)} mb-1 collapsed"
         data-toggle="collapsing" data-target="#consumer-groups-issues-${clusterIdentifier}">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        CONSUMER GROUPS ISSUES
    </div>
    <div id="consumer-groups-issues-${clusterIdentifier}" class="collapseable pb-2">
        ${consumerGroupsCountsTooltip}
    </div>
</#if>


<#if fullyOk>
    <div class="alert alert-success">
        NO_ISSUES
    </div>
</#if>

<#if clusterIssues?size == 0>
    <span style="display: none;">NO_ISSUES</span> <#-- to be searchable -->
</#if>
