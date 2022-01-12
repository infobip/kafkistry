
<#-- @ftlvariable name="principal" type="java.lang.String" -->
<#-- @ftlvariable name="unwantedClusterRules" type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.kafka.KafkaAclRule>>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/bulkDeletePrincipalUnwantedRules.js"></script>
    <script src="static/bulkUtil.js"></script>
    <title>Kafkistry: Delete ACL rules</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as aclUtil>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Delete all unwanted ACLs on cluster</h2>
    <hr/>

    <h4>Going to Delete ACL rules on cluster</h4>
    <br/>
    <p><strong>Principal:</strong> ${principal}</p>

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
        <#list unwantedClusterRules as clusterIdentifier, rules>
            <tr class="unwanted-rules-cluster thead-light" data-cluster-identifier="${clusterIdentifier}">
                <th colspan="100">${clusterIdentifier}</th>
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
                    <#assign statusId = "op-status-"+clusterIdentifier>
                    <#include "../../common/serverOpStatus.ftl">
                </td>
            </tr>
        </#list>
    </table>
    <br/>

    <#if unwantedClusterRules?size gt 0>
        <p><label>Enter word DELETE to confirm what you are about to do <input type="text" id="delete-confirm"></label></p>

        <button id="bulk-delete-principal-rules-btn" class="btn btn-danger btn-sm" data-principal="${principal}">
            Delete missing ACLs on all clusters (${unwantedClusterRules?size})
        </button>
    <#else>
        <p><i>No clusters need deletion of ACl rules</i></p>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#assign statusId = "">
    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>