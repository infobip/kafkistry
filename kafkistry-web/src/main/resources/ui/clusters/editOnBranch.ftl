<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="title" type="java.lang.String" -->
<#-- @ftlvariable name="clusterRequest" type="com.infobip.kafkistry.service.history.ClusterRequest" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/addCluster.js?ver=${lastCommit}"></script>
    <script src="static/cluster/editCluster.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterForm.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterDryRunInspect.js?ver=${lastCommit}"></script>
    <title>Kafkistry: ${title}</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h2>${title}</h2>

    <p>Branch name: <strong>${branch}</strong></p>
    <hr>

    <#if clusterRequest.errorMsg??>
        <div role="alert" class="alert alert-danger" title="${clusterRequest.errorMsg}">
            Corrupted content!
        </div>
        <p><strong>Cause:</strong> <span style="color: red;">${clusterRequest.errorMsg}</span></p>
        <p>Please fix corruption in storage files</p>
    </#if>

    <#if clusterRequest.cluster??>
        <#assign cluster = clusterRequest.cluster>
        <#assign showDryRunInspect = clusterExists>
        <#include "clusterForm.ftl">

        <br/>

        <#switch clusterRequest.type>
            <#case "ADD">
                <button id="add-cluster-btn" class="btn btn-primary btn-sm">Edit pending import to registry</button>
                <#break>
            <#case "UPDATE">
                <button class="btn btn-primary btn-sm" id="edit-cluster-btn">Edit pending edit request</button>
                <#break>
            <#case "DELETE">
                <div role="alert" class="alert alert-danger">
                    Can't edit DELETE operation
                </div>
                <#break>
            <#default>
                <strong>Cant perform change</strong>
                <br/>Having change type: ${clusterRequest.type}
                <#break>
        </#switch>
    </#if>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#if clusterRequest.cluster??>
        <#include "../common/serverOpStatus.ftl">
        <br/>

        <#include "../common/entityYaml.ftl">
    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>