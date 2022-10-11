<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<#import "../../common/documentation.ftl" as doc>
<#import "../../common/infoIcon.ftl" as info>
<#import "../../common/selectLocation.ftl" as commonLocComp>

<#macro topicProperties partitions replication>
    <div class="form-group row properties-row">
        <label class="col-8">Partition count</label>
        <input class="col-4 form-control" type="number" name="partitionCount"
               value="${partitions}" title="Partition count"/>
    </div>
    <div class="form-group row properties-row">
        <label class="col-8">Replication factor</label>
        <input class="col-4 form-control" type="number" name="replicationFactor"
               value="${replication}" title="Replication factor"/>
    </div>
</#macro>

<#macro configEntry key, value, doc>
    <tr class="config-entry">
        <td>${key} <@info.icon tooltip=doc?replace('"', "'")/></td>
        <td>
            <label class="width-full">
                <input class="width-full conf-value-in" type="text" name="${key}" value="${value}">
                <span class="small text-primary conf-value-out"></span>
            </label>
        </td>
        <td>
            <button class="remove-config-btn btn btn-outline-danger btn-sm">x</button>
        </td>
    </tr>
</#macro>

<#macro configEntries config>
<#-- @ftlvariable name="config" type="java.util.Map<java.lang.String, java.lang.String>" --->
    <table class="config table table-sm borderless">
        <tr class="">
            <th>Config key</th>
            <th>Value</th>
            <th></th>
        </tr>
        <#list config as key, value>
            <@configEntry key=key value=value doc=(existingValues.topicConfigDoc[key])!''/>
        </#list>
        <tfoot>
        <tr class="no-hover">
            <td colspan="100">
                <select class="config-key-select form-control" title="Add config entry"
                        data-live-search="true" data-size="8" data-style="alert-primary">
                    <#list existingValues.commonTopicConfig as key, entry>
                        <option value="${key}" data-value="${entry.value}"
                                data-doc="${(existingValues.topicConfigDoc[key]?replace('"', "'"))!''}">${key}</option>
                    </#list>
                </select>
            </td>
        </tr>
        </tfoot>
    </table>
</#macro>

<#macro clusterOverride clusterIdentifier clusterTag overrides topicGlobalProperties>
<#-- @ftlvariable name="overrides" type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.String>>" -->
<#-- @ftlvariable name="topicGlobalProperties" type="com.infobip.kafkistry.model.TopicProperties" -->
    <div class="cluster-override">
        <div class="row alert-secondary p-1 pt-3 rounded-top">
            <div class="col">
                <@commonLocComp.selectLocation selectedIdentifier=clusterIdentifier selectedTag=clusterTag/>
                <div class="move-override-up-btn btn btn-sm btn-outline-info" title="Move override UP">&uarr;</div>
                <div class="move-override-down-btn btn btn-sm btn-outline-info" title="Move override DOWN">&darr;</div>
            </div>
            <div class="col"></div>
            <div class="col-">
                <div class="bg-white rounded">
                    <button class="remove-cluster-btn btn btn-outline-primary btn-outline-danger">
                        - Remove override
                    </button>
                </div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-4">
                <#assign properties = overrides["properties"]!topicGlobalProperties>
                <#assign propertiesOverriden = overrides["properties"]??>
                <div class="form-group row">
                    <div role="button" class="col mt-1">
                        <label class="form-control" style="cursor: pointer;">
                            Override partition replica properties
                            <input name="propertiesOverridden" type="checkbox" <#if propertiesOverriden>checked</#if>>
                            <@info.icon tooltip=doc.enablePropertiesOverrides/>
                        </label>
                    </div>
                </div>
                <@topicProperties partitions=properties.partitionCount replication=properties.replicationFactor/>
            </div>
            <div class="col-md-8 pt-1">
                <#assign config = overrides["config"]!{}>
                <@configEntries config=config/>
            </div>
        </div>
    </div>
</#macro>