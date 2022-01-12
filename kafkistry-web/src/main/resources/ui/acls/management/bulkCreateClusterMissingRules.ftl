
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="missingPrincipalRules" type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.kafka.KafkaAclRule>>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/bulkCreateClusterMissingRules.js"></script>
    <script src="static/bulkUtil.js"></script>
    <title>Kafkistry: Create ACL rules</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as aclUtil>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Bulk create missing ACLs on cluster</h2>
    <hr/>

    <h4>Going to create all principal ACL rules on cluster</h4>
    <br/>
    <p><strong>Cluster:</strong> ${clusterIdentifier}</p>
    <hr/>

    <table class="table table-bordered">
        <thead class="thead-dark">
        <tr>
            <th>Principal</th>
            <th>Host</th>
            <th>Resource</th>
            <th>Operation</th>
            <th>Policy</th>
        </tr>
        </thead>
        <#list missingPrincipalRules as principal, rules>
            <tr class="missing-rules-principal thead-light" data-principal="${principal}">
                <th colspan="100">${principal} - #Rules: ${rules?size}</th>
            </tr>
            <#list rules as rule>
                <tr>
                    <td>${rule.principal}</td>
                    <td>${rule.host}</td>
                    <td><@aclUtil.resource resource = rule.resource/></td>
                    <td><@aclUtil.operation type = rule.operation.type/></td>
                    <td><@aclUtil.policy policy = rule.operation.policy/></td>
                </tr>
            </#list>
            <tr>
                <td colspan="100">
                    <#assign statusId = "op-status-"+principal?replace(":", "_")>
                    <#include "../../common/serverOpStatus.ftl">
                </td>
            </tr>
        </#list>
    </table>
    <br/>

    <#if missingPrincipalRules?size gt 0>
        <button id="bulk-create-cluster-rules-btn" class="btn btn-primary btn-sm" data-cluster-identifier="${clusterIdentifier}">
            Create missing ACLs on all principals (${missingPrincipalRules?size})
        </button>
    <#else>
        <p><i>No principals need creation of ACl rules</i></p>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#assign statusId = "">
    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>