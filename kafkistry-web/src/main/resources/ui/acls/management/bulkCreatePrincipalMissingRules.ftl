
<#-- @ftlvariable name="principal" type="java.lang.String" -->
<#-- @ftlvariable name="missingClusterRules" type="java.util.Map<java.lang.String, java.util.List<com.infobip.kafkistry.kafka.KafkaAclRule>>" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/bulkCreatePrincipalMissingRules.js"></script>
    <script src="static/bulkUtil.js"></script>
    <title>Kafkistry: Create ACL rules</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as aclUtil>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Bulk create missing ACLs on clusters</h2>
    <hr/>

    <h4>Going to create ACL rules on clusters</h4>
    <br/>
    <p><strong>Principal:</strong> ${principal}</p>
    <hr/>

    <table class="table table-hover table-bordered">
        <thead class="table-theme-dark">
        <tr>
            <th>Principal</th>
            <th>Host</th>
            <th>Resource</th>
            <th>Operation</th>
            <th>Policy</th>
        </tr>
        </thead>
        <#list missingClusterRules as clusterIdentifier, rules>
            <tr class="missing-rules-cluster thead-light" data-cluster-identifier="${clusterIdentifier}">
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

    <#if missingClusterRules?size gt 0>
        <button id="bulk-create-principal-rules-btn" class="btn btn-primary btn-sm" data-principal="${principal}">
            Create missing ACLs on all clusters (${missingClusterRules?size})
        </button>
    <#else>
        <p><i>No clusters need creation of ACl rules</i></p>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#assign statusId = "">
    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>