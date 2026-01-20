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
<#-- @ftlvariable name="autoContinuation" type="java.lang.Boolean" -->
<#-- @ftlvariable name="autoContinuationAfterEnd" type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/recordsStructure-js/model.js?ver=${lastCommit}"></script>
    <script src="static/consume/consume.js?ver=${lastCommit}"></script>
    <script src="static/consume/consumeFilter.js?ver=${lastCommit}"></script>
    <script src="static/consume/consumeKeyPartition.js?ver=${lastCommit}"></script>
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

<div class="container pt-2">
    <div class="mb-1">
        <label>
            <#if !clusterIdentifier?? && allClusters?size == 1>
                <#assign clusterIdentifier = allClusters?first>
            </#if>
            Cluster:
            <select name="cluster" class="form-control mr-sm-1 selectPicker"
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
                   class="topic-input form-control mr-sm-1" value="${topicName!""}">
        </label>
        <label>
            <a id="inspect-topic-btn" href="#" class="btn btn-outline-secondary">Inspect 🔍</a>
        </label>

        <div class="clearfix mb-3"></div>
        <label>
            Max records:
            <input type="number" value="${(numRecords?c)!'20'}" name="numRecords" class="form-control form-control-sm mr-sm-1">
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
            <br/>
            <button id="determine-partition-btn" class="btn btn-sm btn-outline-secondary" type="button">
                Determine partition for key...
            </button>
        </label>


        <div id="determine-partition-form" style="display: none;" class="bg-body-tertiary rounded border p-1">
            <div class="clearfix"></div>
            <label>
                Key:
                <input value="" name="messageKey" placeholder="enter message key..." class="form-control form-control-sm">
            </label>
            <label>
                Interpret key as:
                <select name="keySerializer" class="form-control form-control-sm" title="Interpret key as:">
                    <option value="STRING" selected>String</option>
                    <option value="BASE64">Base 64</option>
                    <option value="INTEGER">Integer</option>
                    <option value="LONG">Long</option>
                    <option value="FLOAT">Float</option>
                    <option value="DOUBLE">Double</option>
                    <option value="SHORT">Short</option>
                </select>
            </label>
            <label>
                <br/>
                <#assign resolveTooltip>
                    Resolve in which partition message with given key would be produced into.
                    Resolution is determined by <code>hash</code><i>(murmur2)</i> of message key <code>%</code><i>(modulo)</i> number of partitions in topic.<br/>
                    <strong>NOTE:</strong> this is correct only if:
                    <ul>
                        <li>message was produced with non-<code>null</code> key</li>
                        <li>default built-in partitioner is used on producer side</li>
                        <li>no new partitions were added to the topic</li>
                    </ul>
                </#assign>
                <button id="resolve-partition-for-key-btn" class="btn btn-sm btn-outline-secondary" type="button">
                    Resolve <@info.icon tooltip=resolveTooltip/>
                </button>
                &nbsp;
                <span id="resolved-partition-for-key-result" class="font-monospace"></span>
            </label>
            <div class="clearfix"></div>
            <#assign statusId = "resolve-partition">
            <#include "../common/serverOpStatus.ftl">
            <#assign statusId = "">
        </div>

        <div class="clearfix"></div>
        <label class="offset-type-form">
            <span class="mr-sm-1">Read starting from: </span>
            <label class="d-flex align-items-center gap-2">
            <select name="offsetType" class="form-control form-control-sm mr-sm-1">
                <option value="LATEST" <#if ((offsetType.name())!'') == 'LATEST'>selected</#if>>Latest minus</option>
                <option value="EARLIEST" <#if ((offsetType.name())!'') == 'EARLIEST'>selected</#if>>Earliest plus</option>
                <option value="EXPLICIT" <#if ((offsetType.name())!'') == 'EXPLICIT'>selected</#if>>Explicit offset of</option>
                <option value="TIMESTAMP" <#if ((offsetType.name())!'') == 'TIMESTAMP'>selected</#if>>UNIX timestamp</option>
            </select>
            <input name="offset" type="number" value="${(offset?c)!'100'}" class="form-control form-control-sm mr-sm-1">
            <span id="picked-time-indicator" class="time text-primary small" style="display: none;">---</span>
            </label>
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

        <div class="clearfix mb-2"></div>
        <div class="d-flex align-items-center gap-2">
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="checkbox" id="readOnlyCommitted" name="readOnlyCommitted"
                       <#if (readOnlyCommitted?? && readOnlyCommitted)>checked</#if>>
                <label class="form-check-label" for="readOnlyCommitted">
                    Read only transactionally committed records
                </label>
            </div>
        </div>

        <div class="clearfix"></div>
        <div class="d-flex align-items-center gap-2">
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="checkbox" id="filterEnabled" name="filterEnabled">
                <label class="form-check-label" for="filterEnabled">Use records filtering</label>
            </div>
            <span class="p-1 pe-2">
                Help: <@info.icon tooltip=doc.filterHelp />
            </span>
            <span class="small">Note: filter is just linear search over records</span>
        </div>

        <div id="filter-container" style="display: none;">
            <#include "readFilters.ftl">
        </div>

        <div class="clearfix"></div>
        <label>
            Auto-continuation
            <#assign autoContinuationDoc>
                When <code>enabled</code>, reading/consume completed with <code>timeout</code>
                and without any records matching criteria, consume will then automatically trigger continue
                where previous consume read left off. <br/>
                When <code>disabled</code>, no continuation will be triggered after <code>timeout</code>.
            </#assign>
            <@info.icon tooltip=autoContinuationDoc/>
        </label>
        <label class="mx-2">
            <input class="mr-1" type="checkbox" name="auto-continuation" <#if (autoContinuation?? && autoContinuation)>checked</#if>>
            <span class="mr-sm-1">enabled</span>
        </label>
        <label>
            <input class="mr-1" type="checkbox" name="auto-continuation-after-end" <#if (autoContinuationAfterEnd?? && autoContinuationAfterEnd)>checked</#if>>
            <span class="mr-sm-1">
                continue after end
                <#assign autoContinuationonEndDoc>
                    When <code>enabled</code>, auto-continuation will continue even when end of topic is reached
                    (up-to newest messages), basically waiting for new messages to get produced.
                    When <code>disabled</code>, auto-continuation will stop after reaching end of topic (end of all partitions).
                </#assign>
                <@info.icon tooltip=autoContinuationonEndDoc/>
            </span>
        </label>

        <div class="clearfix mb-2"></div>
        <button type="button" class="consume-trigger-btn btn btn-primary w-100" id="consume-btn">
            Start reading
        </button>
        <button type="button" class="consume-stop-btn btn btn-danger w-100" id="stop-btn" style="display: none;">
            Stop reading
        </button>
    </div>

    <br/>
    <#include "../common/serverOpStatus.ftl">
    <div id="error-container"></div>
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
