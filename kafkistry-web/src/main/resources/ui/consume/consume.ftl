<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterTopics" type="java.util.Map<java.lang.String, java.util.List<java.lang.String>>" -->
<#-- @ftlvariable name="allClusters" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="numRecords" type="java.lang.Integer" -->
<#-- @ftlvariable name="partitions" type="java.lang.String" -->
<#-- @ftlvariable name="notPartitions" type="java.lang.String" -->
<#-- @ftlvariable name="maxWaitMs" type="java.lang.Long" -->
<#-- @ftlvariable name="waitStrategy" type="com.infobip.kafkistry.service.consume.WaitStrategy" -->
<#-- @ftlvariable name="offsetType" type="com.infobip.kafkistry.service.consume.OffsetType" -->
<#-- @ftlvariable name="offset" type="java.lang.Long" -->
<#-- @ftlvariable name="availableDeserializerTypes" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="keyDeserializerType" type="java.lang.String" -->
<#-- @ftlvariable name="valueDeserializerType" type="java.lang.String" -->
<#-- @ftlvariable name="headersDeserializerType" type="java.lang.String" -->
<#-- @ftlvariable name="readFilterJson" type="java.lang.String" -->
<#-- @ftlvariable name="readOnlyCommitted" type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/consume/consume.js?ver=${lastCommit}"></script>
    <script src="static/consume/consumeFilter.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Consume</title>
    <meta name="current-nav" content="nav-consume"/>
</head>

<style>
    .topic-input {
        width: 500px !important;
    }
    #filterEnabled:focus {
        box-shadow:none !important;
    }
</style>

<script>
    let readFilter = null;
    try {
        <#if readFilterJson??>
            readFilter = ${readFilterJson?no_esc};
        </#if>
    } catch (e) {}
</script>

<body>

<#include "../commonMenu.ftl">
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<div class="container">
    <form class="mb-1">
        <label>
            <#if !clusterIdentifier?? && allClusters?size == 1>
                <#assign clusterIdentifier = allClusters?first>
            </#if>
            Cluster:
            <select name="cluster" class="form-control form-control-sm mr-sm-1 selectPicker"
                    data-live-search="true" data-size="6">
                <option value="" disabled <#if !(clusterIdentifier??)>selected</#if>>Select cluster...</option>
                <#list allClusters as cluster>
                    <option value="${cluster}"
                            <#if clusterIdentifier?? && clusterIdentifier==cluster>selected</#if>>${cluster}</option>
                </#list>
            </select>
        </label>
        <label>
            Topic:
            <input type="search" placeholder="Select topic..." name="topic"
                   class="topic-input form-control form-control-sm mr-sm-1" value="${topicName!""}">
        </label>
        <label>
            <a id="inspect-topic-btn" href="#" class="btn btn-sm btn-outline-secondary">Inspect 🔍</a>
        </label>

        <div class="clearfix"></div>
        <label>
            Max records:
            <input type="number" value="${(numRecords?c)!'20'}" name="numRecords" class="form-control form-control-sm mr-sm-1">
        </label>
        <label>
            Partitions:
            <input value="${partitions!''}" name="partitions" placeholder="2,3,5,7... or blank"
                   class="form-control form-control-sm mr-sm-1">
        </label>
        <label>
            Not partitions:
            <input value="${notPartitions!''}" name="notPartitions" placeholder="2,3,5,7,... or blank"
                   class="form-control form-control-sm mr-sm-1">
        </label>
        <label>
            Max wait time ms:
            <input type="number" value="${(maxWaitMs?c)!'10000'}" name="maxWaitMs" class="form-control form-control-sm mr-sm-1">
        </label>
        <label>
            Wait strategy:
            <select name="waitStrategy" class="form-control form-control-sm mr-sm-1">
                <option value="AT_LEAST_ONE" <#if ((waitStrategy.name())!'') == 'AT_LEAST_ONE'>selected</#if>>
                    Wait for at least one message
                </option>
                <option value="WAIT_NUM_RECORDS" <#if ((waitStrategy.name())!'') == 'WAIT_NUM_RECORDS'>selected</#if>>
                    Wait all (max records specified) to be read
                </option>
            </select>
        </label>

        <div class="clearfix"></div>
        <label class="form-inline">
            <span class="mr-sm-1">Read starting from: </span>
            <select name="offsetType" class="form-control form-control-sm mr-sm-1">
                <option value="LATEST" <#if ((offsetType.name())!'') == 'LATEST'>selected</#if>>Latest minus</option>
                <option value="EARLIEST" <#if ((offsetType.name())!'') == 'EARLIEST'>selected</#if>>Earliest plus</option>
                <option value="EXPLICIT" <#if ((offsetType.name())!'') == 'EXPLICIT'>selected</#if>>Explicit offset of</option>
                <option value="TIMESTAMP" <#if ((offsetType.name())!'') == 'TIMESTAMP'>selected</#if>>UTC timestamp milliseconds</option>
            </select>
            <input name="offset" type="number" value="${(offset?c)!'100'}" class="form-control form-control-sm mr-sm-1">
        </label>

        <div class="clearfix"></div>
        <label>
            Key deserialization
            <select class="form-control form-control-sm mr-sm-1" name="keyDeserializerType">
                <option>AUTO</option>
                <#list availableDeserializerTypes as deserializerType>
                    <option <#if deserializerType == (keyDeserializerType!'')>selected</#if>>${deserializerType}</option>
                </#list>
            </select>
        </label>
        <label>
            Value deserialization
            <select class="form-control form-control-sm mr-sm-1" name="valueDeserializerType">
                <option>AUTO</option>
                <#list availableDeserializerTypes as deserializerType>
                    <option <#if deserializerType == (valueDeserializerType!'')>selected</#if>>${deserializerType}</option>
                </#list>
            </select>
        </label>
        <label>
            Headers deserialization
            <select class="form-control form-control-sm mr-sm-1" name="headersDeserializerType">
                <option>AUTO</option>
                <#list availableDeserializerTypes as deserializerType>
                    <option <#if deserializerType == (headersDeserializerType!'')>selected</#if>>${deserializerType}</option>
                </#list>
            </select>
        </label>

        <div class="clearfix"></div>
        <label class="form-inline">
            <input class="mr-1" type="checkbox" name="readOnlyCommitted" <#if (readOnlyCommitted?? && readOnlyCommitted)>checked</#if>>
            <span class="mr-sm-1">Read only transactionally committed records </span>
        </label>

        <div class="clearfix"></div>
        <div class="form-inline">
            <label class="form-check-inline">
                <input id="filterEnabled" name="filterEnabled" type="checkbox" class="form-control form-control-sm mr-1">
                <span>Use records filtering</span>
            </label>
            <span class="p-1 pr-2">
                Help: <@info.icon tooltip=doc.filterHelp />
            </span>
            <span class="small">Note: filter is just linear search over records</span>
        </div>

        <div id="filter-container" style="display: none;">
            <#include "readFilters.ftl">
        </div>

        <br/>
        <div class="clearfix"></div>
        <button type="button" class="btn btn-primary form-control" id="consume-btn">
            Start reading
        </button>
    </form>

    <br/>
    <#include "../common/serverOpStatus.ftl">
    <div id="messages-container"></div>
</div>

<div style="display: none;">
    <#list clusterTopics as clusterIdentifier,topics>
        <div class="cluster-topics" data-cluster-identifier="${clusterIdentifier}">
            <#list topics as topic>
                <div class="data-topic-name" data-topic="${topic}"></div>
            </#list>
        </div>
    </#list>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>