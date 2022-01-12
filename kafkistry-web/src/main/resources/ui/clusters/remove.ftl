<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/removeClusterFromRegistry.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Remove cluster</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../commonMenu.ftl">

<div class="container">
    <h1>Remove cluster from registry</h1>
    <hr>

    <br/>
    <p>Removing cluster '${cluster.identifier}'</p>
    <button id="delete-btn" class="btn btn-danger btn-sm" data-cluster-identifier="${cluster.identifier}">
        Remove cluster from registry
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/createPullRequestReminder.ftl">

    <#include "../common/serverOpStatus.ftl">
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>