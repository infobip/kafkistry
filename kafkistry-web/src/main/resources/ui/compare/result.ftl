
<#-- @ftlvariable name="compareRequest" type="com.infobip.kafkistry.service.topic.compare.ComparingRequest" -->
<#-- @ftlvariable name="result" type="com.infobip.kafkistry.service.topic.compare.ComparingResult" -->
<#-- @ftlvariable name="legend" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.compare.ComparedValue>" -->
<#-- @ftlvariable name="topicConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->

<#import "../common/infoIcon.ftl" as cInfo>
<#import "../common/util.ftl" as util>

<#macro displayValue name comparedValue>
<#-- @ftlvariable name="comparedValue" type="com.infobip.kafkistry.service.topic.compare.ComparedValue" -->
    <#assign boldClass = comparedValue.configDefined?then("font-weight-bold", "")>
    <#assign value = (comparedValue.value)!'---'>
    <#assign cellStyle = "">
    <#assign statusClass = "">
    <#switch comparedValue.status>
        <#case "NOT_PRESENT">
            <#assign statusClass = "text-secondary">
            <#break>
        <#case "MISMATCH">
            <#assign statusClass = "text-danger">
            <#assign cellStyle = "background-color: #f8d6da;">
            <#break>
        <#case "HAS_MISMATCH">
            <#assign statusClass = "text-primary">
            <#assign cellStyle = "background-color: #bfd7ff;">
            <#break>
        <#case "MATCHING">
            <#assign statusClass = "text-dark">
            <#break>
        <#case "UNDEFINED">
            <#assign statusClass = "text-muted font-italic">
            <#break>
    </#switch>
    <td style="${cellStyle}">
        <span class="${statusClass} ${boldClass} conf-value" data-name="${name}" data-value="${value}">${value}</span>
    </td>
</#macro>

<div style="overflow-x: scroll;">
<table class="compare-result table table-sm table-striped table-bordered">
    <thead class="thead-dark">
    <tr>
        <th></th>
        <th>
            Base:
            <#if compareRequest.source.topicName??><br/>${compareRequest.source.topicName}</#if>
            <#if compareRequest.source.onClusterIdentifier??><br/>${compareRequest.source.onClusterIdentifier}</#if>
        </th>
        <#list compareRequest.targets as t>
            <th>
                Target ${t?index + 1}:
                <#if t.topicName??><br/>${t.topicName}</#if>
                <#if t.onClusterIdentifier??><br/>${t.onClusterIdentifier}</#if>
            </th>
        </#list>
    </tr>
    <tr>
        <th>
            Status of topic on cluster →<br/>
            Property ↓
        </th>
        <th>
            <#list result.inspectionStatusTypes.sourceTypes as statusType>
                <@util.namedTypeStatusAlert type = statusType alertInline=false/>
            </#list>
        </th>
        <#list result.inspectionStatusTypes.targetsTypes as targetTypes>
            <th>
                <#list targetTypes as statusType>
                    <@util.namedTypeStatusAlert type = statusType alertInline=false/>
                </#list>
            </th>
        </#list>
    </tr>
    </thead>
    <tr>
        <td>partition.count</td>
        <@displayValue name="" comparedValue=result.partitionCount.source/>
        <#list result.partitionCount.targets as t>
            <@displayValue name="partition.count" comparedValue=t/>
        </#list>
    </tr>
    <tr>
        <td>replication.factor</td>
        <@displayValue name="" comparedValue=result.replicationFactor.source/>
        <#list result.replicationFactor.targets as t>
            <@displayValue name="replication.factor" comparedValue=t/>
        </#list>
    </tr>
    <#list result.config as key, comparison>
        <tr>
            <td>${key} <@cInfo.icon tooltip=(topicConfigDoc[key]?replace('"', "'"))!''/></td>
            <@displayValue name=key comparedValue=comparison.source/>
            <#list comparison.targets as t>
                <@displayValue name=key comparedValue=t/>
            </#list>
        </tr>
    </#list>
</table>

<table class="table table-sm table-bordered small" style="width: auto;">
    <thead class="thead-dark">
    <tr>
        <th colspan="100">Legend</th>
    </tr>
    </thead>
    <#list legend as description,comparedValue>
        <tr>
            <td>${description}</td>
            <@displayValue name="" comparedValue=comparedValue/>
        </tr>
    </#list>
</table>

</div>
