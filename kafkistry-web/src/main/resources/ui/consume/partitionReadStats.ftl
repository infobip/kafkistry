<#-- @ftlvariable name="partitionsStats" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.consume.PartitionReadStatus>" -->

<#import "../common/util.ftl" as util>
<#import "progressBar.ftl" as pb>

<table class="table table-hover table-sm m-0">
    <thead class="thead-light">
    <tr>
        <th>Partition</th>
        <th>Processed offsets</th>
        <th>Processed</th>
        <th>Accepted</th>
        <th>Reached end</th>
    </tr>
    </thead>
    <#list partitionsStats as partition, partitionStatus>
        <#macro prettyPercentage value zeroDivDefault partitionStatus>
            <#assign partitionSize = partitionStatus.endOffset - partitionStatus.beginOffset>
            <#if partitionSize <= 0>
                <#assign percentage = "${zeroDivDefault}%">
            <#else>
                <#assign percentage = "${util.prettyNumber(100.0 * value / partitionSize)}%">
            </#if>
            <span class="small">(${percentage})</span>
        </#macro>
        <#macro offsetPercentage offset zeroDivDefault partitionStatus>
            <#assign fromBegin = offset - partitionStatus.beginOffset>
            <@prettyPercentage value=fromBegin zeroDivDefault=zeroDivDefault partitionStatus=partitionStatus/>
        </#macro>
        <tr class="partition-read-status" data-partition="${partition?c}" data-ended-at-offset="${partitionStatus.endedAtOffset?c}">
            <td rowspan="2">${partition}</td>
            <td>
                <div class="row">
                    <div class="col">
                        <code title="Started at offset">${partitionStatus.startedAtOffset}</code>
                        <@offsetPercentage offset=partitionStatus.startedAtOffset zeroDivDefault=0.0 partitionStatus=partitionStatus/>
                        <#if partitionStatus.startedAtTimestamp??>
                            <br/>
                            <span class="small time" data-time="${partitionStatus.startedAtTimestamp?c}"
                                  title="Started at timestamp"></span>
                        </#if>
                    </div>
                    <div class="col-auto">&rarr;</div>
                    <div class="col">
                        <code title="Ended at offset">${partitionStatus.endedAtOffset}</code>
                        <@offsetPercentage offset=partitionStatus.endedAtOffset zeroDivDefault=100.0 partitionStatus=partitionStatus/>
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
                <@prettyPercentage value=partitionStatus.read zeroDivDefault=0.0 partitionStatus=partitionStatus/>
                <#if partitionStatus.startedAtTimestamp?? && partitionStatus.endedAtTimestamp??>
                    <br/>
                    <#assign timeRange = partitionStatus.endedAtTimestamp - partitionStatus.startedAtTimestamp>
                    <span class="small" title="Read time range: endTime - startTime">${util.prettyDuration(timeRange/1000.0)}</span>
                </#if>
            </td>
            <td>${partitionStatus.matching}</td>
            <td>
                <#if partitionStatus.reachedEnd>
                    <span class="badge bg-primary">YES</span>
                <#else>
                    <span class="badge bg-secondary">NO</span>
                </#if>
                <#if partitionStatus.remaining gt 0>
                    <span title="Remaining to end" class="small">
                        ${partitionStatus.remaining}
                        <span title="% of total messages in partition">
                            <@prettyPercentage value=partitionStatus.remaining zeroDivDefault=0.0 partitionStatus=partitionStatus/>
                        </span>
                    </span>
                </#if>
            </td>
        </tr>
        <tr>
            <td colspan="100">
                <#assign skip = partitionStatus.startedAtOffset-partitionStatus.beginOffset>
                <#assign preRetention = 0>
                <#if skip lt 0>
                    <#assign preRetention = -skip>
                    <#assign skip = 0>
                </#if>
                <#assign read = partitionStatus.read>
                <#assign remain = partitionStatus.remaining>
                <#assign totalSum = preRetention + skip + read + remain>
                <@pb.progressBar total=totalSum preRetention=preRetention skip=skip read=read remain=remain/>
            </td>
        </tr>
    </#list>
</table>
