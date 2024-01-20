<#-- @ftlvariable name="partitionsStats" type="java.util.Map<java.lang.Integer, com.infobip.kafkistry.service.consume.PartitionReadStatus>" -->

<#import "../common/util.ftl" as util>

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
    <#list partitionsStats as partition, partitionStatus>
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
                    <span title="Remaining to end" class="small">(${partitionStatus.remaining})</span>
                </#if>
            </td>
        </tr>
    </#list>
</table>
