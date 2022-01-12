
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="enums"  type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, ? extends java.lang.Object>>" -->

<#-- @ftlvariable name="rule" type="com.infobip.kafkistry.model.AclRule" -->

<div class="rule">
    <form onsubmit="return false;">
        <div class="row">
            <div class="col">
                <#if (rule.presence)??>
                    <#assign presence = rule.presence>
                </#if>
                <#include "../../common/presenceForm.ftl">
            </div>
            <div class="col-">
                <button role="button" class="remove-rule-btn btn btn-sm btn-outline-danger">x</button>
            </div>
        </div>
        <div class="ruleData form-row pt-3">
            <div class="col">
                <#assign host = (rule.host)!''>
                <input type="text" name="host" placeholder="host ip(s) or * for any" value="${host}" class="form-control"
                       title="Host">
            </div>
            <#assign selectedResourceType = (rule.resource.type.name())!''>
            <div class="col">
                <select name="resourceType" class="form-control" title="Resource">
                    <#assign resourceTypes = enums["com.infobip.kafkistry.model.AclResource$Type"]>
                    <#list resourceTypes as resourceType, enum>
                        <#assign selected = resourceType == selectedResourceType>
                        <option <#if selected>selected</#if>>${resourceType}</option>
                    </#list>
                </select>
            </div>
            <div class="col">
                <#assign name = (rule.resource.name)!''>
                <#if ((rule.resource.namePattern.name())!'') == "PREFIXED">
                    <#assign name = name + "*">
                </#if>
                <input type="text" name="resourceName" placeholder="resource name" class="form-control" value="${name}">
            </div>
            <div class="col">
                <#assign selectedOperationType = (rule.operation.type.name())!''>
                <select name="operationType" class="form-control" title="Operation">
                    <#assign operationTypes = enums["com.infobip.kafkistry.model.AclOperation$Type"]>
                    <#list operationTypes as operationType, enum>
                        <#assign selected = operationType == selectedOperationType>
                        <option <#if selected>selected</#if>>${operationType}</option>
                    </#list>
                </select>
            </div>
            <div class="col">
                <#assign selectedPolicyType = (rule.operation.policy.name())!''>
                <select name="policyType" class="form-control" title="Policy">
                    <#assign policyTypes = enums["com.infobip.kafkistry.model.AclOperation$Policy"]>
                    <#list policyTypes as policyType, enum>
                        <#assign selected = policyType == selectedPolicyType>
                         <option <#if selected>selected</#if>>${policyType}</option>
                    </#list>
                </select>
            </div>
        </div>
    </form>
    <hr/>
</div>