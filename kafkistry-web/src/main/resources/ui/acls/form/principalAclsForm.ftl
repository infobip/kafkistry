<#-- @ftlvariable name="newName" type="java.lang.String" -->
<#-- @ftlvariable name="principalSourceType" type="java.lang.String" -->
<#-- @ftlvariable name="principalExists" type="java.lang.Boolean" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<#-- @ftlvariable name="principalAcls" type="com.infobip.kafkistry.model.PrincipalAclRules" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->


<#import "../../common/documentation.ftl" as doc>
<#import "../../common/infoIcon.ftl" as info>

<div hidden="hidden" id="yaml-source-metadata"
     data-source-type="${principalSourceType}"
     data-id="<#if !(newName??)>${principalAcls.principal}</#if>"
     data-id-param-name="principal"
     data-field-name="principalAcls"
     data-api-path-part="acls"
     data-branch-name="${branch!''}"
     data-object-exists="${(principalExists!false)?then("yes", "no")}"
>
</div>

<div hidden="hidden" id="principal-source-metadata"
     data-principal-source-type="${principalSourceType}"
     data-principal-name="<#if !(newName??)>${principalAcls.principal}</#if>"
     data-branch-name="${branch!''}"
     data-principal-exists="${(principalExists!false)?then("yes", "no")}"
>
</div>

<div class="card">
    <div class="card-header">
        <span class="h4">Principal</span>
    </div>
    <div class="card-body pb-0">
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Principal:</label>
            <#if newName??>
                <input class="form-control col-sm-10" style="width: 500px;" type="text" name="principal"
                       placeholder="enter principal name... (e.g. 'User:example')"
                >
            <#else>
                <span class="text-monospace form-control-plaintext col-sm-10"
                      id="fixed-principal">${principalAcls.principal}</span>
            </#if>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Description:</label>
            <textarea class="form-control col-sm-10" name="description"
                      placeholder="Enter description, may contain jira task like ABC-123">${(principalAcls.description)!''}</textarea>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Owner:</label>
            <input class="form-control col-sm-10" type="text" name="owner" title="Owner" value="${(principalAcls.owner)!''}">
        </div>
    </div>
</div>
<br/>

<div class="card">
    <div class="card-header">
        <span class="h4">Rules</span>
    </div>
    <div class="card-body">
        <div id="rules">
            <#if principalAcls??>
                <#list principalAcls.rules as rule>
                    <#include "aclRule.ftl">
                </#list>
            </#if>
        </div>
        <div class="float-right">
            <button id="add-rule-btn" role="button" class="btn btn-sm btn-primary">Add ACL rule</button>
        </div>
    </div>
</div>


<div id="rule-template" class="template" style="display: none;">
    <#assign rule = {}>
    <#include "aclRule.ftl">
</div>

<br/>

<button id="dry-run-inspect-acls-btn" class="btn btn-secondary btn-sm mt-2">
    Dry run inspect principal ACLs <@info.icon tooltip=doc.dryRunTopicInspectAclsBtn/>
</button>

<#assign statusId = "dryRunInspectAcls">
<#include "../../common/serverOpStatus.ftl">
<#assign statusId = "">

<div id="dry-run-inspect-acls-status"></div>

<br/>

<#include "../../common/updateForm.ftl">

<#include "../../common/existingValues.ftl">