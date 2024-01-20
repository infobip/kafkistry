<#-- @ftlvariable name="recordsResult" type="com.infobip.kafkistry.service.consume.KafkaRecordsResult" -->

<#import "../common/util.ftl" as util>

<#assign records = recordsResult.records>

<#if records?size == 0>
    <#assign msgAlertClass = "alert-warning">
    <#assign msg = "No records received from total of ${recordsResult.totalCount} record(s)">
<#else>
    <#assign msgAlertClass = "alert-primary">
    <#assign msg = "Got ${records?size} record(s) from total of ${recordsResult.totalCount} record(s)">
</#if>

<#if recordsResult.timedOut>
    <#assign msg = msg + ", wait has timed out">
</#if>
<#if recordsResult.reachedEnd>
    <#assign msg = msg + ", reading reached latest">
</#if>

<div style="display: none" id="consume-status-data"
    data-totalCount="${recordsResult.totalCount?c}"
    data-resultCount="${records?size?c}"
    data-timedOut="${recordsResult.timedOut?c}"
    data-reachedEnd="${recordsResult.reachedEnd?c}"></div>

<div class="alert ${msgAlertClass}">${msg}</div>

<button type="button" class="consume-trigger-btn btn btn-secondary form-control" id="continue-consume-btn">
    Continue reading
</button>
<div class="spacing"></div>

<div class="card">
    <div id="partition-stats-toggler" class="card-header"
         data-toggle="collapsing" data-target="#partition-stats-container" title="Expand/collapse...">
        <span class="h5">Partition read stats</span>
    </div>
    <div class="card-body p-0 collapseable" id="partition-stats-container">
        <table class="table table-sm m-0">
            <thead class="thead-light">
            <tr>
                <th>Partition</th>
                <th>Processed offsets</th>
                <th>Processed</th>
                <th>Accepted</th>
                <th>Reached end</th>
            </tr>
            </thead>
            <#list recordsResult.partitions as partition, partitionStatus>
                <tr class="partition-read-status" data-partition="${partition?c}" data-ended-at-offset="${partitionStatus.endedAtOffset?c}">
                    <td>${partition}</td>
                    <td>
                        <div class="row">
                            <div class="col">
                                <code title="Started at offset">${partitionStatus.startedAtOffset}</code>
                                <#if partitionStatus.startedAtTimestamp??>
                                    <br/>
                                    <span class="small time" data-time="${partitionStatus.startedAtTimestamp?c}"
                                          title="Started at timestamp"></span>
                                </#if>
                            </div>
                            <div class="col-">&rarr;</div>
                            <div class="col">
                                <code title="Ended at offset">${partitionStatus.endedAtOffset}</code>
                                <#if partitionStatus.endedAtTimestamp??>
                                    <br/>
                                    <span class="small time" data-time="${partitionStatus.endedAtTimestamp?c}"
                                          title="Ended at timestamp"></span>
                                </#if>
                            </div>
                        </div>
                    </td>
                    <td>
                        <span title="Number of records: endOffset-startOffset">${partitionStatus.read}</span>
                        <#if partitionStatus.startedAtTimestamp?? && partitionStatus.endedAtTimestamp??>
                            <br/>
                            <#assign timeRange = partitionStatus.endedAtTimestamp - partitionStatus.startedAtTimestamp>
                            <span class="small" title="Read time range: endTime - startTime">${util.prettyDuration(timeRange/1000.0)}</span>
                        </#if>
                    </td>
                    <td>${partitionStatus.matching}</td>
                    <td>
                        <#if partitionStatus.reachedEnd>
                            <span class="badge badge-primary">YES</span>
                        <#else>
                            <span class="badge badge-secondary">NO</span>
                        </#if>
                        <#if partitionStatus.remaining gt 0>
                            <span title="Remaining to end" class="small">
                                (${partitionStatus.remaining})
                            </span>
                        </#if>
                    </td>
                </tr>
            </#list>
        </table>
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
                    <span>Leader epoch</span>: <code class="leader-epoch record-metadata-value">${(record.leaderEpoch?c)!'N/A'}</code>
                    <span>Topic</span>: <code class="topic record-metadata-value">${record.topic}</code>
                    <br/>
                    <span>Key size</span>: <code class="topic record-metadata-value">${record.keySize}</code>
                    <span>Headers size</span>: <code class="topic record-metadata-value">${record.headersSize}</code>
                    <span>Value size</span>: <code class="topic record-metadata-value">${record.valueSize}</code>
                    <br/>
                    <span class="text-nowrap">
                        <span>Timestamp</span>:
                        <span class="badge badge-secondary record-timestamp-type" data-timestamp-type="${record.timestampType.name()}">${record.timestampType}</span>
                        <code class="timestamp record-metadata-value" data-timestamp="${record.timestamp?c}">${record.timestamp?c}</code>
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
