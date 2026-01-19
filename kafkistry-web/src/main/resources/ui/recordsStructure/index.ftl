<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicNames" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="topicTypes" type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, com.infobip.kafkistry.model.PayloadType>>" -->
<#-- @ftlvariable name="clusterIdentifiers" type="java.util.List<java.lang.String>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/recordsStructure-js/menu.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Records Structure</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<div class="container">

    <div>
        <h3>Records structure</h3>
        <a class="btn btn-sm btn-outline-info" href="${appUrl.recordsStructure().showDryRun()}">
            Inspect how analyzer works
        </a>
        <hr/>
    </div>
    <div class="ui-helper-clearfix"></div>

    <div class="form-group">
        <div class="row g-2 mb-2">
            <label class="col-2">Cluster</label>
            <div class="col">
                <select title="select cluster..." name="clusterIdentifier" class="selectPicker">
                    <#assign allHtml>
                        <span class='badge bg-primary'>All clusters combined</span>
                    </#assign>
                    <option value="ALL" selected data-content="${allHtml}"></option>
                    <#list clusterIdentifiers as clusterIdentifier>
                        <#assign clusterHtml>
                            <span class='badge bg-neutral'>CLUSTER</span> ${clusterIdentifier}
                        </#assign>
                        <option value="${clusterIdentifier}" data-content="${clusterHtml}"></option>
                    </#list>
                </select>
            </div>
        </div>
        <div class="row g-2 mb-2">
            <label class="col-2">Topic</label>
            <div class="col">
                <select title="select topic..." name="topicName" class="selectPicker"
                        data-live-search="true" data-size="6">
                    <#list topicNames as topicName>
                        <#assign topicHtml>
                            <span class='mr-3'>
                            <#assign payloadTypes = []>
                            <#if (topicTypes[topicName])??>
                                <#list topicTypes[topicName] as cluster, payloadType>
                                    <#if !payloadTypes?seq_contains(payloadType)>
                                        <#assign payloadTypes = payloadTypes + [payloadType]>
                                    </#if>
                                </#list>
                            </#if>
                            <#if payloadTypes?size gt 0>
                                <#list payloadTypes as payloadType>
                                    <span class='badge bg-neutral'>${payloadType}</span>
                                </#list>
                            <#else>
                                <span class='badge bg-warning'>no data</span>
                            </#if>
                            </span>
                            <span>${topicName}</span>
                        </#assign>
                        <option value="${topicName}" data-content="${topicHtml}"></option>
                    </#list>
                </select>
            </div>
        </div>
    </div>

    <a id="inspect-records-structure-btn">
        <button type="button" class="btn btn-primary">
            Inspect records structure
        </button>
    </a>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
