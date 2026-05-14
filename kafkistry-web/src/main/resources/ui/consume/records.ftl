<#-- @ftlvariable name="recordsResult" type="com.infobip.kafkistry.service.consume.KafkaRecordsResult" -->
<#-- @ftlvariable name="overallSkipCount" type="java.lang.Long" -->
<#-- @ftlvariable name="overallReadCount" type="java.lang.Long" -->
<#-- @ftlvariable name="overallPartitions" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.consume.PartitionReadStatus>" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="unmaskedRevealEnabled" type="java.lang.Boolean" -->
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
    <div class="text-body">
    <@pb.progressBar total=totalSum preRetention=preRetention skip=totalSkipCount read=totalReadCount remain=recordsResult.remainingCount
        emHeight=2 legend=true/>
    </div>
</div>

<button type="button" class="consume-trigger-btn btn btn-secondary w-100" id="continue-consume-btn">
    Continue reading
</button>
<div class="spacing"></div>

<div class="card">
    <div id="partition-stats-toggler" class="card-header collapsed"
         data-toggle="collapsing" data-bs-target="#partition-stats-container" title="Expand/collapse...">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        <span class="h5">Partition read stats</span>
    </div>
    <div class="card-body p-0 collapseable" id="partition-stats-container">
        <#include "partitionReadStats.ftl">
    </div>
</div>

<div class="consuming-records-indicator">
    <div class="d-flex mt-2">
        <div class="mx-auto d-flex align-items-center">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
            <div class="mx-3">
                Reading/waiting records...
            </div>
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading...</span>
            </div>
        </div>
    </div>
</div>

<br/>
<div class="spacing"></div>

<div class="row">
    <div class="col-auto">
        <label>Control</label>
        <div class="width-full">
            <button id="expand-all-btn" class="btn btn-sm btn-secondary">Expand all</button>
            <button id="collapse-all-btn" class="btn btn-sm btn-secondary">Collapse all</button>
        </div>
    </div>
    <div class="col"></div>
    <div class="col-auto">
        <label>Show</label>
        <div class="show-flags">
            <label class="btn btn-sm btn-outline-secondary mouse-pointer">
                Metadata <input type="checkbox" name="showMetadata" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary mouse-pointer">
                Key <input type="checkbox" name="showKey" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary mouse-pointer">
                Headers <input type="checkbox" name="showHeaders" checked>
            </label>
            <label class="btn btn-sm btn-outline-secondary mouse-pointer">
                Value <input type="checkbox" name="showValue" checked>
            </label>
        </div>
    </div>
    <div class="col"></div>
    <div class="col-auto">
        <label>Extra</label>
        <div class="width-full">
            <button id="export-btn" class="btn btn-sm btn-secondary">Export to file...</button>
        </div>
    </div>
</div>

<br/>

<#assign recordsSize = records?size>
<#list records as record>
    <#assign recordIndex = record?index>
    <#include "record.ftl">
    <hr/>
</#list>
