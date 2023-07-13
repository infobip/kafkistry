<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="result" type="com.infobip.kafkistry.sql.QueryResult" -->

<#function linkedTypeUrl type value>
    <#if value?is_string>
        <#assign valueStr = value>
    <#elseif value?is_boolean || value?is_number>
        <#assign valueStr = value?c>
    <#else>
        <#assign valueStr = "${value?no_esc?markup_string}">
    </#if>
    <#switch type>
        <#case "TOPIC">
            <#return appUrl.topics().showTopic(valueStr)>
        <#case "CLUSTER">
            <#return appUrl.clusters().showCluster(valueStr)>
        <#case "PRINCIPAL">
            <#return appUrl.acls().showAllPrincipalAcls(valueStr)>
        <#case "QUOTA_ENTITY">
            <#return appUrl.quotas().showEntity(valueStr)>
    </#switch>
    <#return "">
</#function>

<#function compoundLink type resource>
<#-- @ftlvariable name="resource" type="com.infobip.kafkistry.sql.LinkedResource" -->
    <#switch type.name()>
        <#case "CLUSTER_TOPIC">
            <#return appUrl.topics().showInspectTopicOnCluster(resource.topic, resource.cluster)>
        <#case "CLUSTER_GROUP">
            <#return appUrl.consumerGroups().showConsumerGroup(resource.cluster, resource.group)>
        <#case "PRINCIPAL_ACL">
            <#return appUrl.acls().showAllPrincipalAclsRule(resource.principal, resource.acl.toString())>
        <#case "PRINCIPAL_CLUSTER">
            <#return appUrl.acls().showAllPrincipalAclsCluster(resource.principal, resource.cluster)>
        <#case "KSTREAM_APP">
            <#return appUrl.kStream().showKStreamApp(resource.cluster, resource.kafkaStreamAppId)>
    </#switch>
    <#return "">
</#function>

<#function splitCamelCased string>
    <#return string?replace("(?<=[a-z])(?=[A-Z])", " ", "r")?split(" ")>
</#function>

<#macro breakLongString string>
    <#assign parts = string?split("(?<=[,;-])", "r")>
    <#list parts as part><span class="span-block">${part}</span></#list>
</#macro>

<div class="container">
    <p>
        Got result set of <code>${result.count}</code> rows<#if result.totalCount gt result.count>, total count without <strong>LIMIT</strong> would be <code>${result.totalCount}</code></#if>
    </p>
</div>

<div style="overflow-x: scroll;">

<table class="table table-sm table-bordered table-striped text-monospace text-small small">
    <thead class="thead-dark">
    <tr>
        <th>#</th>
        <#list result.linkedCompoundTypes as type>
            <th>
                <span class="badge badge-info">
                ${type.name()?replace("_","-")?lower_case}
                </span>
            </th>
        </#list>
        <#list result.columns as column>
            <th class="column-meta" data-label="${column.name}" data-type="${column.type}" title="${column.name} (${column.type})">
                <#assign parts = splitCamelCased(column.name)>
                <#list parts as part><span class="span-block">${part}</span></#list>
            </th>
        </#list>
    </tr>
    </thead>
    <#if result.rows?size == 0>
        <tr>
            <td colspan="100"><i>(empty)</i></td>
        </tr>
    </#if>
    <#list result.rows as row>
        <tr class="no-hover data-row">
            <td class="text-secondary">${row?index + 1}</td>
            <#if result.linkedCompoundTypes??>
                <#list result.linkedCompoundTypes as type>
                    <td class="p-1">
                        <#if row.linkedResource?? && row.linkedResource.types?seq_contains(type)>
                            <a href="${compoundLink(type, row.linkedResource)}"
                               target="_blank"
                               class="btn-xsmall btn btn-outline-dark"
                               title="Open ${type.name()?lower_case?replace('_', '-')}..."
                            >🔍</a>
                        <#else>
                            ---
                        </#if>
                    </td>
                </#list>
            </#if>
            <#list row.values as value>
                <#assign valueRaw><#t>
                    <#if !(value??)><#t>
                        null<#t>
                    <#elseif value?is_string><#t>
                        ${value}<#t>
                    <#else><#t>
                        ${value?c}<#t>
                    </#if><#t>
                </#assign>
                <#assign columnMeta = result.columns[value?index]>
                <td class="data-value sql-${columnMeta.type?lower_case}" data-value="${valueRaw}"
                    title="${columnMeta.name} (${columnMeta.type})">
                    <#if value??>
                        <#assign valueDisplay>
                            <#if value?is_boolean>
                                ${value?c}
                            <#elseif value?is_string>
                                <@breakLongString string=value/>
                            <#else>
                                ${value}
                            </#if>
                        </#assign>
                        <#if result.columnLinkedType?keys?seq_contains(value?index)>
                            <#assign columnLinkedType = result.columnLinkedType?api.get(value?index)>
                            <a target="_blank" href="${linkedTypeUrl(columnLinkedType, value)}">${valueDisplay}</a>
                        <#else>
                            ${valueDisplay}
                        </#if>
                    <#else>
                        <code>null</code>
                    </#if>
                </td>
            </#list>
        </tr>
    </#list>
</table>

</div>
