
<#-- @ftlvariable name="principal" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="rules" type="java.util.List<com.infobip.kafkistry.kafka.KafkaAclRule>" -->
<#-- @ftlvariable name="rule" type="java.lang.String" -->
<#-- @ftlvariable name="needsForceDeletion" type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../../commonResources.ftl"/>
    <script src="static/acls-js/deleteUnwantedRules.js"></script>
    <title>Kafkistry: Delete ACL rules</title>
</head>

<body>

<#include "../../commonMenu.ftl">
<#import "../util.ftl" as aclUtil>

<div id="rules-metadata" style="display: none;"
     data-principal="${principal}"
     data-cluster-identifier="${clusterIdentifier}"
     data-rule="${rule!''}"
></div>

<div class="container">
    <h2><#include "../../common/backBtn.ftl"> Delete unwanted ACLs on cluster</h2>
    <hr/>

    <h4>Going to Delete ACL rules on cluster</h4>
    <br/>
    <p><strong>Principal:</strong> ${principal}</p>
    <p><strong>Cluster:</strong> ${clusterIdentifier}</p>

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
        <#if rules?size == 0>
            <tr>
                <td colspan="100"><i>(no missing rules to create)</i></td>
            </tr>
        </#if>
        <#list rules as rule>
            <tr>
                <td>${rule.principal}</td>
                <td>${rule.host}</td>
                <td><@aclUtil.resource resource = rule.resource/></td>
                <td><@aclUtil.operation type = rule.operation.type/></td>
                <td><@aclUtil.policy policy = rule.operation.policy/></td>
            </tr>
        </#list>
    </table>
    <br/>

    <#if needsForceDeletion>
        <p>
            <label>Force delete <input type="checkbox" name="forceDelete"/></label>
            <i>(delete even if it should exist)</i>
        </p>
    </#if>

    <#if rules?size gt 0>
        <button id="delete-rules-btn" class="btn btn-danger btn-sm">
            Delete unwanted ACLs (${rules?size})
        </button>
    </#if>
    <#include "../../common/cancelBtn.ftl">

    <#include "../../common/serverOpStatus.ftl">
</div>

<#include "../../common/pageBottom.ftl">
</body>
</html>