<#-- @ftlvariable name="currentClusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->
<#-- @ftlvariable name="effectiveClusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->
<#-- @ftlvariable name="diffClusterResources"  type="com.infobip.kafkistry.service.resources.ClusterDiskUsage" -->

<div class="card">
    <div class="card-header">
        <h4>Current disk resource usage</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = currentClusterResources>
        <#include "resourcesInspect.ftl">
    </div>
</div>


<div class="card">
    <div class="card-header">
        <h4>Effective disk resource usage</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = effectiveClusterResources>
        <#include "resourcesInspect.ftl">
    </div>
</div>

<div class="card">
    <div class="card-header">
        <h4>Diff disk resource usage (effective - current)</h4>
    </div>
    <div class="card-body p-0">
        <#assign clusterResources = diffClusterResources>
        <#include "resourcesInspect.ftl">
    </div>
</div>
