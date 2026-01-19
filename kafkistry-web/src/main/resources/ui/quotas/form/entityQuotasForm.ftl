
<#-- @ftlvariable name="newName" type="java.lang.String" -->
<#-- @ftlvariable name="entitySourceType" type="java.lang.String" -->
<#-- @ftlvariable name="quotaEntityExists" type="java.lang.Boolean" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<#-- @ftlvariable name="entityQuotas" type="com.infobip.kafkistry.model.QuotaDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->


<#import "../../common/documentation.ftl" as doc>
<#import "../../common/infoIcon.ftl" as info>

<div hidden="hidden" id="yaml-source-metadata"
     data-source-type="${entitySourceType}"
     data-id="<#if !(newName??)>${entityQuotas.entity.asID()}</#if>"
     data-id-param-name="quotaEntityID"
     data-field-name="quota"
     data-api-path-part="quotas"
     data-branch-name="${branch!''}"
     data-object-exists="${(quotaEntityExists!false)?then("yes", "no")}"
>
</div>

<#assign user = (entityQuotas.entity.user)!''>
<#assign clientId = (entityQuotas.entity.clientId)!''>
<#if user == "<default">
    <#assign user = ''>
</#if>
<#if clientId == "<default">
    <#assign clientId = ''>
</#if>

<#if entityQuotas??>
    <#if (entityQuotas.entity.user)??>
        <#if entityQuotas.entity.user == "<default>">
            <#if (entityQuotas.entity.clientId)??>
                <#if entityQuotas.entity.clientId == "<default>">
                    <#assign selectedEntityType = "DEFAULT_USER_DEFAULT_CLIENT">
                <#else>
                    <#assign selectedEntityType = "DEFAULT_USER_CLIENT">
                </#if>
            <#else>
                <#assign selectedEntityType = "DEFAULT_USER">
            </#if>
        <#else>
            <#if (entityQuotas.entity.clientId)??>
                <#if entityQuotas.entity.clientId == "<default>">
                    <#assign selectedEntityType = "USER_DEFAULT_CLIENT">
                <#else>
                    <#assign selectedEntityType = "USER_CLIENT">
                </#if>
            <#else>
                <#assign selectedEntityType = "USER">
            </#if>
        </#if>
    <#else>
        <#if (entityQuotas.entity.clientId)??>
            <#if entityQuotas.entity.clientId == "<default>">
                <#assign selectedEntityType = "DEFAULT_CLIENT">
            <#else>
                <#assign selectedEntityType = "CLIENT">
            </#if>
        <#else>
            <#-- should not be possible-->
            <#assign selectedEntityType = "USER">
        </#if>
    </#if>
<#else>
    <#assign selectedEntityType = "USER">
</#if>
<#assign entityIdInputDisabled = !(newName??)>


<div class="card">
    <div class="card-header">
        <span class="h4">Quota entity</span>
    </div>
    <div class="card-body pb-0">
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Entity:</label>
            <div class="col-sm-10">
                <div class="form-group row">
                    <div class="col-4">
                        <label>Type</label>
                        <select name="entityType" class="form-control" title="Entity type" <#if entityIdInputDisabled>disabled</#if>>
                            <option value="USER_CLIENT" <#if selectedEntityType == "USER_CLIENT">selected</#if>>
                                User & Client
                            </option>
                            <option value="USER_DEFAULT_CLIENT" <#if selectedEntityType == "USER_DEFAULT_CLIENT">selected</#if>>
                                User & Default Client
                            </option>
                            <option value="USER" <#if selectedEntityType == "USER">selected</#if>>
                                User
                            </option>
                            <option value="DEFAULT_USER_CLIENT" <#if selectedEntityType == "DEFAULT_USER_CLIENT">selected</#if>>
                                Default User & Client
                            </option>
                            <option value="DEFAULT_USER_DEFAULT_CLIENT" <#if selectedEntityType == "DEFAULT_USER_DEFAULT_CLIENT">selected</#if>>
                                Default User & Default Client
                            </option>
                            <option value="DEFAULT_USER" <#if selectedEntityType == "DEFAULT_USER">selected</#if>>
                                Default User
                            </option>
                            <option value="CLIENT" <#if selectedEntityType == "CLIENT">selected</#if>>
                                Client
                            </option>
                            <option value="DEFAULT_CLIENT" <#if selectedEntityType == "DEFAULT_CLIENT">selected</#if>>
                                Default Client
                            </option>
                        </select>
                    </div>
                    <div class="col">
                        <label class="width-full">User</label>
                        <input type="text" name="user" title="Entity user" class="entity-user-input form-control"
                               value="${user}" style="display: none;" <#if entityIdInputDisabled>disabled</#if>>
                        <span class="entity-user-default badge bg-secondary" style="display: none;">DEFAULT</span>
                        <span class="entity-user-any badge bg-neutral" style="display: none;">ANY</span>
                    </div>
                    <div class="col">
                        <label class="width-full">Client Id</label>
                        <input type="text" name="clientId" title="Entity clientId" class="entity-clientId-input form-control"
                               value="${clientId}" style="display: none;" <#if entityIdInputDisabled>disabled</#if>>
                        <span class="entity-clientId-default badge bg-secondary" style="display: none;">DEFAULT</span>
                        <span class="entity-clientId-any badge bg-neutral" style="display: none;">ANY</span>
                    </div>
                </div>
            </div>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Owner:</label>
            <div class="col-sm-10">
                <input class="form-control" type="text" name="owner" title="Owner" value="${(entityQuotas.owner)!''}">
            </div>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Presence:</label>
            <div class="col-sm">
                <#if (entityQuotas.presence)??>
                    <#assign presence = entityQuotas.presence>
                </#if>
                <#include "../../common/presenceForm.ftl">
            </div>
        </div>
    </div>
</div>

<#include "../../common/existingValues.ftl">
<br/>

<#macro quotaPropertiesCells properties>
    <td>
        <div class="conf-value">
            <input title="rate byte/sec" class="conf-value-in form-control" type="text" data-nan-default="none"
                   name="producerByteRate" value="${(properties.producerByteRate?c)!''}">
            <div>
                <span class="conf-value-out small text-primary"></span>
                <span class="conf-help">?</span>
            </div>
        </div>
    </td>
    <td>
        <div class="conf-value">
            <input title="rate byte/sec" class="conf-value-in form-control" type="text" data-nan-default="none"
                   name="consumerByteRate" value="${(properties.consumerByteRate?c)!''}">
            <div>
                <span class="conf-value-out small text-primary"></span>
                <span class="conf-help">?</span>
            </div>
        </div>
    </td>
    <td>
        <input title="request time %" class="conf-value-in form-control" type="number"
               name="requestPercentage" value="${(properties.requestPercentage?c)!''}">
    </td>
</#macro>

<#import "../../common/selectLocation.ftl" as commonLocComp>

<#macro quotaPropertiesOverrideRow properties selectedIdentifier selectedTag>
    <tr class="no-hover quota-override">
        <td><@commonLocComp.selectLocation selectedIdentifier=selectedIdentifier selectedTag=selectedTag /></td>
        <@quotaPropertiesCells properties=properties/>
        <td><span class="remove-quota-override-btn btn btn-sm btn-outline-danger">x</span></td>
    </tr>
</#macro>

<div class="card">
    <div class="card-header">
        <span class="h4">Quota properties</span>
    </div>
    <div class="card-body p-0">
        <table class="table table-hover m-0">
            <thead class="table-theme-dark">
            <tr>
                <th>Location</th>
                <th>Producer byte rate</th>
                <th>Consumer byte rate</th>
                <th>Request percentage</th>
                <th></th>
            </tr>
            </thead>
            <tbody id="quota-properties-rows">
            <tr id="global-properties" class="no-hover">
                <td><label class="alert-sm alert-primary width-full text-center" style="min-width: 160px;">Global</label></td>
                <@quotaPropertiesCells properties=(entityQuotas.properties)!{}/>
                <td></td>
            </tr>
            <#if (entityQuotas)??>
                <#list entityQuotas.clusterOverrides as clusterIdentifier, properties>
                    <@quotaPropertiesOverrideRow properties=properties selectedIdentifier=clusterIdentifier selectedTag=""/>
                </#list>
                <#list entityQuotas.tagOverrides as tag, properties>
                    <@quotaPropertiesOverrideRow properties=properties selectedIdentifier="" selectedTag=tag/>
                </#list>
            </#if>
            </tbody>
            <tfoot>
            <tr class="no-hover">
                <td colspan="100">
                    <div id="add-quota-override-btn" class="btn btn-sm btn-outline-primary float-end">
                        Add cluster/tag override...
                    </div>
                </td>
            </tr>
            </tfoot>
        </table>
    </div>
</div>

<div class="template" style="display: none;">
    <table id="quota-override-row-template">
    <@quotaPropertiesOverrideRow properties={} selectedIdentifier="" selectedTag=""/>
    </table>
</div>

<br/>

<#include "../../common/updateForm.ftl">

