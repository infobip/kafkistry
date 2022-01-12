<#-- @ftlvariable name="pendingTopicsRequests"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.TopicRequest>>" -->
<#-- @ftlvariable name="pendingClustersRequests"  type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.service.ClusterRequest>>" -->
<#-- @ftlvariable name="pendingPrincipalRequests"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.AclsRequest>>" -->
<#-- @ftlvariable name="pendingQuotasRequests"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.quotas.QuotasRequest>" -->

<#assign requests = {
"Topic": pendingTopicsRequests,
"Cluster": pendingClustersRequests,
"Principal ACLs": pendingPrincipalRequests,
"Entity quota": pendingQuotasRequests
}>

<#assign emptyRequests = {}>
<#assign nonEmptyRequests = {}>

<#list requests as entityName, pendingUpdates>
    <#if pendingUpdates?size gt 0>
        <#include "../common/pendingChangeRequests.ftl">
        <br/>
    </#if>
</#list>

<div class="form-row">
    <#list requests as entityName, pendingUpdates>
        <#if pendingUpdates?size == 0>
            <div class="col">
                <#include "../common/pendingChangeRequests.ftl">
            </div>
        </#if>
    </#list>
</div>
