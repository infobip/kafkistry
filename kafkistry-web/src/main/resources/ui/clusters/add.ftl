<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/cluster/addCluster.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterForm.js?ver=${lastCommit}"></script>
    <script src="static/cluster/clusterDryRunInspect.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Add cluster</title>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <h1>Add cluster</h1>
    <hr>
    <div class="test-connection-step form-group row">
        <label class="col">
            <input id="connection" type="text" placeholder="Enter bootstrap servers: host1:port1,host2:port2,..."
                class="form-control">
        </label>
        <div class="col-auto">
            <label class="form-control" style="cursor: pointer;">
            SSL <input id="ssl" type="checkbox">
            </label>
        </div>
        <div class="col-auto">
            <label class="form-control" style="cursor: pointer;">
            SASL <input id="sasl" type="checkbox">
            </label>
        </div>
        <div class="col-auto">
            <select id="profiles" class="kafka-profiles selectpicker" multiple title="Properties profiles">
                <#list existingValues.kafkaProfiles as profile>
                    <option>${profile}</option>
                </#list>
            </select>
        </div>

        <label class="col-2">
            <input id="test-btn" type="submit" value="Test connection"
                   class="btn btn-outline-primary btn-sm">
        </label>
        <br/>
    </div>

    <div class="save-step" style="display: none;">
        <#assign newName = "">
        <#assign clusterSourceType = "NEW">
        <#include "clusterForm.ftl">
    </div>

    <button id="add-cluster-btn" class="save-step btn btn-primary btn-sm" style="display: none;">
        Add cluster to registry
    </button>
    <#include "../common/cancelBtn.ftl">
    <#include "../common/serverOpStatus.ftl">

    <div class="save-step" style="display: none;">
        <#include "../common/createPullRequestReminder.ftl">
        <br/>
        <#include "../common/entityYaml.ftl">
    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>