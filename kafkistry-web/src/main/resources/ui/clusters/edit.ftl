<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/editCluster.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterForm.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterDryRunInspect.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Edit cluster</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Edit cluster</h1>
    <hr>

    <#assign clusterSourceType = "EDIT">
    <#assign clusterExist = true>
    <#include "clusterForm.ftl">

    <button id="edit-cluster-btn" class=" btn btn-primary btn-sm">Edit cluster</button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">

    <#include "../common/createPullRequestReminder.ftl">

    <br/>
    <#include "../common/entityYaml.ftl">

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>