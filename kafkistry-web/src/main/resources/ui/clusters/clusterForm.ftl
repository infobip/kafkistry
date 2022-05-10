<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="cluster" type="com.infobip.kafkistry.model.KafkaCluster" -->
<#-- @ftlvariable name="showDryRunInspect" type="java.lang.Boolean" -->
<#-- @ftlvariable name="clusterSourceType" type="java.lang.String" -->
<#-- @ftlvariable name="clusterExists" type="java.lang.Boolean" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->


<#macro tagInput tag>
    <div class="tag-input form-group row mb-0">
        <label class="col-4">
            <input type="text" name="tag" class="form-control" value="${tag}" placeholder="Tag name...">
        </label>
        <label class="col-">
            <button class="remove-tag-btn btn btn-sm btn-outline-danger mt-1">x</button>
        </label>
    </div>
</#macro>

<div hidden="hidden" id="cluster-source-metadata"
     data-cluster-source-type="${clusterSourceType}"
     data-cluster-identifier="<#if !(newName??)>${cluster.identifier!''}</#if>"
     data-branch-name="${branch!''}"
     data-object-exists="${(clusterExists!false)?then("yes", "no")}"
>
</div>
<div hidden="hidden" id="yaml-source-metadata"
     data-source-type="${clusterSourceType}"
     data-id="<#if !(newName??)>${cluster.identifier!''}</#if>"
     data-id-param-name="clusterIdentifier"
     data-field-name="cluster"
     data-api-path-part="clusters"
     data-branch-name="${branch!''}"
     data-object-exists="${(clusterExists!false)?then("yes", "no")}"
>
</div>


<div class="form-group">
    <div class="form-group row">
        <label class="col-2">Cluster id</label>
        <div class="col-10">
            <input name="clusterId" class="form-control" type="text" title="ClusterId"
                   value="${(cluster.clusterId)!''}"/>
        </div>
    </div>
    <div class="form-group row">
        <label class="col-2">Connection</label>
        <div class="col-10">
            <input name="connectionString" class="form-control" type="text" title="Connction"
                   value="${(cluster.connectionString)!''}" placeholder="Format: host1:port1,host2:port2,..."/>
        </div>
    </div>
    <div class="form-group row">
        <label class="col-2">Properties</label>
        <div class="col- pl-3"></div>
        <div class="col-">
            <label class="mr-2 form-control" style="cursor: pointer;">
                SSL <input name="ssl" type="checkbox" <#if (cluster.sslEnabled)!false>checked</#if>>
            </label>
        </div>
        <div class="col-">
            <label class="mr-2 form-control" style="cursor: pointer;">
                SASL <input name="sasl" type="checkbox" <#if (cluster.saslEnabled)!false>checked</#if>>
            </label>
        </div>
        <div class="col- ml-3">
            <select name="profiles" class="kafka-profiles form-control selectpicker" multiple title="Properties profies">
                <#assign selectedProfiles = (cluster.profiles)![]>
                <#list selectedProfiles as profile>
                    <option value="${profile}" selected>${profile}</option>
                </#list>
                <#list existingValues.kafkaProfiles as profile>
                    <#if !selectedProfiles?seq_contains(profile)>
                        <option value="${profile}">${profile}</option>
                    </#if>
                </#list>
            </select>
        </div>
        <div class="col"></div>
        <div class="col-3">
            <button class="re-test-connection-btn form-control btn btn-info">
                Re-test connection
            </button>
        </div>
    </div>
    <div class="form-group row">
        <label class="col-2">Cluster identifier</label>
        <div class="col-10">
            <#if newName??>
                <input name="clusterIdentifier" class="form-control" type="text" title="clusterIdentifier"
                       placeholder="Human-readable unique cluster identifier...">
            <#else>
                <span class="text-monospace form-control-plaintext col-sm-10"
                      id="fixed-cluster-identifier">${cluster.identifier}</span>
            </#if>
        </div>
    </div>
    <div class="form-group row">
        <label class="col-2">Tags</label>
        <div class="col-10">
            <div class="tags">
                <#list (cluster.tags)![] as tag>
                    <@tagInput tag=tag/>
                </#list>
            </div>
            <button class="add-tag-btn btn btn-sm btn-outline-primary">Add tag...</button>
        </div>
        <div id="tag-template" style="display: none;">
            <@tagInput tag=""/>
        </div>
        <div id="existing-tags" style="display: none;">
            <#list existingValues.tagClusters as tag, clusters>
                <div class="existing-tag" data-tag="${tag}"></div>
            </#list>
        </div>
    </div>

    <#if showDryRunInspect!false>
        <#include "dryRunInspectContainer.ftl">
    </#if>

    <#include "../common/updateForm.ftl">

</div>

