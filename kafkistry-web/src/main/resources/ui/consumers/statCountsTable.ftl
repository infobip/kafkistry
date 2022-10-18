<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="type" type="java.lang.String" -->
<#-- @ftlvariable name="counts" type="java.util.Map<java.lang.Object, java.lang.Integer>" -->

<#import "util.ftl" as cgUtil>
<#import "../common/util.ftl" as util>

<table class="table table-sm">
    <thead class="thead-dark">
    <tr>
        <th colspan="100">${title}</th>
    </tr>
    </thead>
    <#list counts as key, count>
        <tr>
            <td class="status-filter-btn" data-filter-value="${key}">
                <#assign class = "alert-secondary">
                <#switch type>
                    <#case "cluster">
                        <#assign class = "alert-primary">
                        <#break>
                    <#case "lag">
                        <#assign class = util.levelToHtmlClass(key.level)>
                        <#break>
                    <#case "consumer">
                        <#assign class = cgUtil.consumerStatusAlertClass(key)>
                        <#break>
                </#switch>
                <div class="alert ${class}">${key}</div>
            </td>
            <td>${count}</td>
        </tr>
    </#list>
    <#if counts?size == 0>
        <tr>
            <td colspan="100">(empty)</td>
        </tr>
    </#if>
</table>
