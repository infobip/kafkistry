<#-- @ftlvariable name="clusterAcls"  type="com.infobip.kafkistry.service.acl.ClusterAclsInspection" -->

<#import "../common/util.ftl" as util>

<table class="table table-sm m-0">
    <#list clusterAcls.status.statusCounts as statusTypeCount>
        <#assign statusType = statusTypeCount.type>
        <#assign count = statusTypeCount.quantity>
        <tr>
            <td class="status-filter-btn agg-count-status-type" data-status-type="${statusType.name}"
                title="Click to filter by..." data-table-id="acls">
                <#assign stateClass = util.levelToHtmlClass(statusType.level)>
                <div class="alert alert-sm ${stateClass} mb-0">
                    ${statusType.name}
                </div>
            </td>
            <td style="text-align: right;">${count}</td>
        </tr>
    </#list>
</table>
