<#-- @ftlvariable name="recordsResult" type="com.infobip.kafkistry.service.consume.KafkaRecordsResult" -->
<#-- @ftlvariable name="overallSkipCount" type="java.lang.Long" -->
<#-- @ftlvariable name="overallReadCount" type="java.lang.Long" -->
<#-- @ftlvariable name="overallPartitions" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.consume.PartitionReadStatus>" -->
<#-- @ftlvariable name="json" type="com.fasterxml.jackson.databind.ObjectMapper" -->

<#import "../common/util.ftl" as util>
<#import "progressBar.ftl" as pb>

<#assign records = recordsResult.records>

<#if overallPartitions??>
    <#assign totalReadCount = overallReadCount>
    <#assign totalSkipCount = overallSkipCount>
    <#assign partitionsStats = overallPartitions>
<#else>
    <#assign partitionsStats = recordsResult.partitions>
    <#assign totalSkipCount = recordsResult.skipCount>
    <#assign totalReadCount = recordsResult.readCount>
</#if>

<#assign statusMessages = []>
<#if records?size == 0>
    <#assign msgAlertClass = "alert-warning">
    <#assign statusMessages += ["No records received"]>
<#else>
    <#assign msgAlertClass = "alert-primary">
    <#assign statusMessages += ["Got ${records?size} record(s)"]>
</#if>

<#assign statusMessages += ["Read iteration processed ${recordsResult.readCount} record(s)"]>
<#if recordsResult.totalRecordsCount == 0>
    <#assign statusMessages += ["Processed no record(s), topic is empty"]>
<#else>
    <#assign statusMessages += ["Processed ${totalReadCount} record(s), ${util.prettyNumber(100.0*totalReadCount/recordsResult.totalRecordsCount)}% of ${recordsResult.totalRecordsCount} total records in topic partition(s)"]>
    <#if totalSkipCount gt 0>
        <#assign statusMessages += ["Skipped first ${totalSkipCount} record(s) (${util.prettyNumber(100.0*totalSkipCount/recordsResult.totalRecordsCount)}% of total in partitions) due to 'Read starting from' options"]>
    </#if>
    <#if recordsResult.remainingCount gt 0>
        <#assign statusMessages += ["Remaining ${recordsResult.remainingCount} record(s) (${util.prettyNumber(100.0*recordsResult.remainingCount/recordsResult.totalRecordsCount)}% of total in partitions) to reach end "]>
    </#if>
</#if>

<#assign preRetention = 0>
<#list partitionsStats as partition, partitionStatus>
    <#assign skip = partitionStatus.startedAtOffset-partitionStatus.beginOffset>
    <#if skip lt 0>
        <#assign preRetention += -skip>
    </#if>
</#list>
<#if preRetention gt 0>
    <#assign statusMessages += ["${preRetention} record(s) have been processed, but now were deleted by retention on topic"]>
</#if>

<#if recordsResult.timedOut>
    <#assign statusMessages += ["Wait has timed out"]>
</#if>
<#if recordsResult.reachedEnd>
    <#assign statusMessages += ["Reading reached latest"]>
</#if>

<style>
    .progress-bar-section {
        min-width: 0.25em;
        overflow: hidden;
        font-size: 0.75em;
    }
    .progress-bar-empty {
        width: 0 !important;
        min-width: 0 !important;
    }
    .progress-bar-section.deleted {
        background-color: rgb(200, 50, 20);
    }
    .progress-bar-section.skipped {
        background-color: rgb(133, 133, 133);
    }
    .progress-bar-section.processed {
        background-color: rgb(40, 180, 40);
    }
    .progress-bar-section.remaining {
        background-color: rgb(75, 120, 222);
    }
</style>

<div style="display: none" id="consume-status-data"
     data-readCount="${recordsResult.readCount?c}"
     data-resultCount="${records?size?c}"
     data-timedOut="${recordsResult.timedOut?c}"
     data-reachedEnd="${recordsResult.reachedEnd?c}"
     data-partitionStats="${json.writeValueAsString(partitionsStats)}"
></div>

<div class="alert ${msgAlertClass}">
    <strong>Read status:</strong><br/>
    <ul>
        <#list statusMessages as msg>
            <li>${msg}</li>
        </#list>
    </ul>
    <#assign totalSum = preRetention + totalSkipCount + totalReadCount + recordsResult.remainingCount>
    <@pb.progressBar total=totalSum preRetention=preRetention skip=totalSkipCount read=totalReadCount remain=recordsResult.remainingCount
        emHeight=2 legend=true/>
</div>

<button type="button" class="consume-trigger-btn btn btn-secondary form-control" id="continue-consume-btn">
    Continue reading
</button>
<div class="spacing"></div>

<div class="card">
    <div id="partition-stats-toggler" class="card-header collapsed"
         data-toggle="collapsing" data-target="#partition-stats-container" title="Expand/collapse...">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        <span class="h5">Partition read stats</span>
    </div>
    <div class="card-body p-0 collapseable" id="partition-stats-container">
        <#include "partitionReadStats.ftl">
    </div>
</div>
<br/>
<div class="spacing"></div>

<div class="form-row">
    <div class="col-3">
        <label>Control</label>
        <div class="width-full">
            <button id="expand-all-btn" class="btn btn-sm btn-secondary">Expand all</button>
            <button id="collapse-all-btn" class="btn btn-sm btn-secondary">Collapse all</button>
        </div>
    </div>
    <div class="col-5">
        <label>Show</label>
        <div class="width-full form-row show-flags">
            <label class="btn btn-sm btn-outline-secondary m-1 mouse-pointer">
                Metadata <input type="checkbox" name="showMetadata" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary m-1 mouse-pointer">
                Key <input type="checkbox" name="showKey" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary m-1 mouse-pointer">
                Headers <input type="checkbox" name="showHeaders" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary m-1 mouse-pointer">
                Value <input type="checkbox" name="showValue" checked>
            </label>
        </div>
    </div>
    <div class="col-4">
        <label>Extra</label>
        <div class="width-full">
            <button id="export-btn" class="btn btn-sm btn-secondary">Export to file...</button>
        </div>
    </div>
</div>

<br/>

<#list records as record>
    <div class="record card">
        <div class="card-header h6">Record ${record?index+1} of ${records?size}</div>
        <div class="card-body">
            <div class="form-row metadata-row mb-2">
                <div class="col-1">
                    <strong>Metadata</strong>:
                </div>
                <div class="col">
                    <span>Partition</span>: <code class="partition record-metadata-value">${record.partition}</code>
                    <span>Offset</span>: <code class="offset record-metadata-value">${record.offset?c}</code>
                    <span>Leader epoch</span>: <code
                            class="leader-epoch record-metadata-value">${(record.leaderEpoch?c)!'N/A'}</code>
                    <span>Topic</span>: <code class="topic record-metadata-value">${record.topic}</code>
                    <br/>
                    <span>Key size</span>: <code class="topic record-metadata-value">${record.keySize}</code>
                    <span>Headers size</span>: <code class="topic record-metadata-value">${record.headersSize}</code>
                    <span>Value size</span>: <code class="topic record-metadata-value">${record.valueSize}</code>
                    <br/>
                    <span class="text-nowrap">
                        <span>Timestamp</span>:
                        <span class="badge badge-secondary record-timestamp-type"
                              data-timestamp-type="${record.timestampType.name()}">${record.timestampType}</span>
                        <code class="timestamp record-metadata-value"
                              data-timestamp="${record.timestamp?c}">${record.timestamp?c}</code>
                        <code class="time" data-time="${record.timestamp?c}"></code>
                    </span>
                </div>
            </div>
            <div class="form-row key-row mb-2">
                <div class="col-1">
                    <strong>Key</strong>:
                </div>
                <div class="col">
                    <#assign kafkaValue = record.key>
                    <#include "kafkaValue.ftl">
                </div>
            </div>
            <div class="form-row headers-row mb-2">
                <div class="col-1">
                    <strong>Headers</strong>:
                </div>
                <div class="col">
                    <#if record.headers?size == 0>
                        <span><i>(none)</i></span>
                    <#else>
                        <div class="record-value headers-collapsed">
                            <pre class="renderjson"><#t>
                                <span class="disclosure">⊕</span><#t>
                                {<#t>
                                <a href="#" class="headers-expand" onclick="return false;"><#t>
                                    ${record.headers?size} item<#if record.headers?size gt 1>s</#if><#t>
                                </a><#t>
                                }<#t>
                            </pre>
                        </div>
                        <div class="headers-expanded" style="display: none;">
                            <pre class="renderjson"><#t>
                                <a href="#" class="headers-collapse" onclick="return false;"><#t>
                                    <span class="disclosure">⊖</span><#t>
                                </a><#t>
                            </pre>
                            <table class="table table-sm table-borderless m-0">
                                <#list record.headers as header>
                                    <tr class="record-header">
                                        <td class="form-row pl-0">
                                            <div class="col">
                                                <div class="record-value renderjson" data-type="key">
                                                    <span class="key">${header.key}</span>
                                                </div>
                                            </div>
                                        </td>
                                        <td class="pr-0">
                                            <#assign kafkaValue = header.value>
                                            <#include "kafkaValue.ftl">
                                        </td>
                                    </tr>
                                </#list>
                            </table>
                        </div>
                    </#if>
                </div>
            </div>
            <div class="form-row value-row mb-2">
                <div class="col-1">
                    <strong>Value</strong>:
                </div>
                <div class="col">
                    <#assign kafkaValue = record.value>
                    <#include "kafkaValue.ftl">
                </div>
            </div>
        </div>
    </div>
    <hr/>
</#list>
