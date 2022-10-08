
<#-- @ftlvariable name="newName" type="java.lang.String" -->
<#-- @ftlvariable name="topicSourceType" type="java.lang.String" -->
<#-- @ftlvariable name="topicExists" type="java.lang.Boolean" -->
<#-- @ftlvariable name="branch" type="java.lang.String" -->

<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->


<#import "../../common/documentation.ftl" as doc>
<#import "../../common/infoIcon.ftl" as info>

<style>
    .bootstrap-select {
        max-width: 700px !important;
    }
</style>

<div hidden="hidden" id="yaml-source-metadata"
     data-source-type="${topicSourceType}"
     data-id="<#if !(newName??)>${topic.name}</#if>"
     data-id-param-name="topicName"
     data-field-name="topic"
     data-api-path-part="topics"
     data-branch-name="${branch!''}"
     data-object-exists="${(topicExists!false)?then("yes", "no")}"
>
</div>

<div class="card">
    <div class="card-header h3">Basic metadata</div>
    <div class="card-body pb-0">
        <div class="form-group row">
            <label class="col-sm-2 col-form-label" for="topic-name">Name</label>
            <#if newName??>
                <input id="topic-name" class="form-control col-sm-10" type="text" name="topicName"
                       placeholder="enter topic name..." value="${newName}">
            <#else>
                <span class="text-monospace form-control-plaintext col-sm-10">${topic.name}</span>
            </#if>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label" for="owner-name">Owner</label>
            <input id="owner-name" type="text" name="owner" placeholder="Enter owner name..." value="${topic.owner}"
                   class="form-control col-sm-10"/>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label" for="description-text">Description</label>
            <textarea id="description-text" class="form-control col-sm-10" name="description" rows="5"
                      placeholder="Description what is in the topic, JIRA...">${topic.description}</textarea>
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label" for="producer-name">Producer</label>
            <input id="producer-name" type="text" name="producer" placeholder="Which service is producing to topic..."
                   value="${topic.producer}" class="form-control col-sm-10">
        </div>
        <div class="form-group row">
            <label class="col-sm-2 col-form-label">Presence</label>
            <div id="presence" class="col-sm-10 p-1">
                <#assign presence = topic.presence>
                <#include "../../common/presenceForm.ftl">
            </div>
        </div>
    </div>
</div>

<#include "../../common/existingValues.ftl">

<hr/>

<div class="card">
    <div class="card-header h3">Expected resource requirements</div>
    <div class="card-body m-0">
        <div class="resource-requirements-input <#if !topic.resourceRequirements??>disabled</#if> width-full">
            <div class="form-group row">
                <div class="ml-3">
                    <label class="col-form-label form-control">
                        Defined <input class="" type="checkbox" name="resourceRequirementsDefined"
                                       <#if topic.resourceRequirements??>checked</#if>>
                    </label>
                </div>
            </div>
            <#if topic.resourceRequirements??>
                <#assign resourceRequirements = topic.resourceRequirements>
            </#if>
            <#include "topicResourceRequirements.ftl">
            <#import "topicFormConfigComponents.ftl" as comp>

            <div class="resource-input-row">
                <div class="row">
                    <div class="col"></div>
                    <div class="col-">
                        <button id="apply-requirements-show-opts" class="btn btn-sm btn-outline-info">
                            Apply requirements to config...
                            <#assign applyTooltip>
                                Apply means changing various configuration properties based on given expectations.
                                For example, adjusting <code>retention.bytes</code>, <code>retention.ms</code>, etc...<br/>
                                Apply action only modifies current UI's form properties to conform given requirements.<br/>
                                Minimization of config is also applied during apply.<br/>
                                Check diff at bottom of page to get info about what has been changed.
                            </#assign>
                            <@info.icon tooltip = applyTooltip/>
                        </button>
                    </div>
                    <div class="col"></div>
                </div>
                <div id="apply-requirements-menu" class="m-2" style="display: none;">
                    <div class="row">
                        <div class="col"></div>
                        <div class="col-">
                            <div class="m-1">
                                <button id="apply-requirements-for-all" class="btn btn-sm btn-primary">
                                    Apply for all
                                    <#assign applyTooltip>
                                        Apply needed config adjusting for <strong>all</strong> relevant clusters.
                                    </#assign>
                                    <@info.icon tooltip = applyTooltip/>
                                </button>
                                <button id="apply-requirements-for-selected" class="btn btn-sm btn-success">
                                    Apply only for selected
                                    <#assign applyTooltip>
                                        Apply needed config adjusting only for <strong>selected</strong> clusters or
                                        clusters having selected tag.
                                    </#assign>
                                    <@info.icon tooltip = applyTooltip/>
                                </button>
                            </div>
                        </div>
                        <div class="col"></div>
                    </div>
                    <div class="row">
                        <div class="col"></div>
                        <div class="m-1 col- apply-requirements-menu-item">
                            <@comp.commonLocComp.selectLocation selectedIdentifier="" selectedTag="" multi=true/>
                        </div>
                        <div class="col"></div>
                    </div>
                </div>
                <#assign statusId = "applyResourceRequirementsStatus">
                <#include "../../common/serverOpStatus.ftl">
                <#assign statusId = "">
            </div>

        </div>
    </div>
</div>
<br/>

<#import "topicFormConfigComponents.ftl" as components>

<div style="display: none;">
    <#assign configEntryTemplate>
        <@components.configEntry key="%key-PH%" value="%value-PH%" doc="%doc-PH%"/>
    </#assign>
    <#outputformat "plainText">
        <#-- want double escaping for jquery manipulation -->
        <div id="config-entry-template">${configEntryTemplate?markup_string?html}</div>
    </#outputformat>
</div>

<div class="card">
    <div class="card-header h3">Global configuration</div>
    <div class="card-body m-0 globalConfig">
        <div class="row">
            <div class="col-md-4">
                <@components.topicProperties
                partitions=topic.properties.partitionCount
                replication=topic.properties.replicationFactor/>
            </div>
            <div class="col-md-8">
                <@components.configEntries config=topic.config/>
            </div>
        </div>
    </div>
</div>
<br/>

<#assign clustersOverrides = {}>
<#assign tagsOverrides = {}>
<#list topic.perClusterProperties as clusterIdentifier, properties>
    <#assign clustersOverrides = clustersOverrides + {clusterIdentifier: {"properties": properties}}>
</#list>
<#list topic.perTagProperties as tag, properties>
    <#assign tagsOverrides = tagsOverrides + {tag: {"properties": properties}}>
</#list>
<#list topic.perClusterConfigOverrides as clusterIdentifier, config>
    <#if clustersOverrides[clusterIdentifier]??>
        <#assign clustersOverrides = clustersOverrides + {clusterIdentifier: {"config": config, "properties": clustersOverrides[clusterIdentifier]["properties"]}}>
    <#else>
        <#assign clustersOverrides = clustersOverrides + {clusterIdentifier: {"config": config}}>
    </#if>
</#list>
<#list topic.perTagConfigOverrides as tag, config>
    <#if tagsOverrides[tag]??>
        <#assign tagsOverrides = tagsOverrides + {tag: {"config": config, "properties": tagsOverrides[tag]["properties"]}}>
    <#else>
        <#assign tagsOverrides = tagsOverrides + {tag: {"config": config}}>
    </#if>
</#list>


<div class="card">
    <div class="card-header h3">Per cluster/tag overrides</div>
    <div class="card-body m-0">

        <#assign topicGlobalProperties = topic.properties>
        <div id="clusters">
            <#list tagsOverrides as tag, tagOverrides>
                <@components.clusterOverride
                clusterIdentifier = ""
                clusterTag = tag
                overrides = tagOverrides
                topicGlobalProperties = topic.properties/>
            </#list>
            <#list clustersOverrides as clusterIdentifier, clusterOverrides>
                <@components.clusterOverride
                clusterIdentifier = clusterIdentifier
                clusterTag = ""
                overrides = clusterOverrides
                topicGlobalProperties = topic.properties/>
            </#list>
        </div>

        <button id="add-cluster-override-btn" class="btn btn-outline-primary btn-sm float-right p-2">
            + Add per-cluster/per-tag override
        </button>

        <div id="cluster-override-template" class="template" style="display: none;">
            <@components.clusterOverride
            clusterIdentifier = "" clusterTag = "" overrides = {}
            topicGlobalProperties = topic.properties/>
        </div>

    </div>
</div>

<br>
<hr/>

<button id="dry-run-inspect-btn" class="btn btn-secondary btn-sm mt-2">
    Dry run inspect config <@info.icon tooltip=doc.dryRunTopicInspectBtn/>
</button>

<#assign statusId = "dryRunInspect">
<#include "../../common/serverOpStatus.ftl">
<#assign statusId = "">

<div id="dry-run-inspect-status"></div>

<br/>

<#include "../../common/updateForm.ftl">