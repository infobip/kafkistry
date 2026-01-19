<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="pendingUpdates"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.history.PendingRequest>>" -->
<#-- @ftlvariable name="entityName"  type="java.lang.String" -->

<div class="card pending-updates-container">
    <div class="card-header">
        <h5>Pending ${entityName} updates</h5>
    </div>
    <div class="card-body p-0">
        <#if pendingUpdates?size == 0>
            <i>(no pending update requests)</i>
        <#else>
            <table class="table table-hover table-sm mb-0">
                <thead>
                <tr class="table-theme-dark">
                    <th>${entityName}</th>
                    <th>Pending branch/commits</th>
                </tr>
                </thead>
                <#list pendingUpdates as id, pendingRequests>
                    <tr class="table-row">
                        <td>
                            <#assign onlyAdd = true>
                            <#list pendingRequests as pendingRequest>
                                <#if pendingRequest.type.name() != "ADD">
                                    <#assign onlyAdd = false>
                                    <#break>
                                </#if>
                            </#list>
                            <#if onlyAdd>
                                ${id}
                            <#elseif pendingRequests?first.class.getSimpleName() == "TopicRequest">
                                <a href="${appUrl.topics().showTopic(id)}">${id}</a>
                            <#elseif pendingRequests?first.class.getSimpleName() == "ClusterRequest">
                                <a href="${appUrl.clusters().showCluster(id)}">${id}</a>
                            <#elseif pendingRequests?first.class.getSimpleName() == "AclsRequest">
                                <a href="${appUrl.acls().showAllPrincipalAcls(id)}">${id}</a>
                            <#elseif pendingRequests?first.class.getSimpleName() == "QuotasRequest">
                                <a href="${appUrl.quotas().showEntity(id)}">${id}</a>
                            <#else>
                                ${id}
                            </#if>
                        </td>
                        <td>
                            <#include "pendingChanges.ftl">
                        </td>
                    </tr>
                </#list>
            </table>
        </#if>
    </div>
</div>
