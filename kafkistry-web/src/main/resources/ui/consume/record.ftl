<#-- @ftlvariable name="record" type="com.infobip.kafkistry.service.consume.KafkaRecord" -->
<#-- @ftlvariable name="recordIndex" type="java.lang.Integer" -->
<#-- @ftlvariable name="recordsSize" type="java.lang.Integer" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="unmaskedRevealEnabled" type="java.lang.Boolean" -->

<#assign headersHaveMasked = false>
<#list record.headers as header>
    <#if header.value.masked><#assign headersHaveMasked = true></#if>
</#list>
<#assign recordHasMasked = record.key.masked || record.value.masked || headersHaveMasked>

<div class="record card"
     data-partition="${record.partition?c}"
     data-offset="${record.offset?c}">
    <div class="card-header h6 d-flex align-items-center">
        <span class="flex-grow-1">Record ${recordIndex + 1} of ${recordsSize}</span>
        <#if (unmaskedRevealEnabled!false) && recordHasMasked>
            <button type="button"
                    class="show-sensitive-data-btn btn btn-sm btn-outline-warning"
                    data-cluster-identifier="${clusterIdentifier}"
                    data-topic-name="${topicName}"
                    data-partition="${record.partition?c}"
                    data-offset="${record.offset?c}"
                    data-record-index="${recordIndex?c}"
                    data-records-size="${recordsSize?c}"
                    title="Re-read record from Kafka with masking bypassed; the access will be audited.">
                🔓 Show sensitive data...
            </button>
        </#if>
    </div>
    <div class="card-body">
        <div class="row g-2 metadata-row mb-2">
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
                    <span class="badge bg-secondary record-timestamp-type"
                          data-timestamp-type="${record.timestampType.name()}">${record.timestampType}</span>
                    <code class="timestamp record-metadata-value"
                          data-timestamp="${record.timestamp?c}">${record.timestamp?c}</code>
                    <code class="time" data-time="${record.timestamp?c}"></code>
                </span>
            </div>
        </div>
        <div class="row g-2 key-row mb-2">
            <div class="col-1">
                <strong>Key</strong>:
            </div>
            <div class="col">
                <#assign kafkaValue = record.key>
                <#include "kafkaValue.ftl">
            </div>
        </div>
        <div class="row g-2 headers-row mb-2">
            <div class="col-1">
                <strong>Headers</strong>:
            </div>
            <div class="col p-0">
                <#if record.headers?size == 0>
                    <span><i>(none)</i></span>
                <#else>
                    <div class="record-value headers-collapsed mx-1">
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
                        <pre class="renderjson px-1"><#t>
                            <a href="#" class="headers-collapse" onclick="return false;"><#t>
                                <span class="disclosure">⊖</span><#t>
                            </a><#t>
                        </pre>
                        <table class="table table-sm table-borderless m-0">
                            <#list record.headers as header>
                                <tr class="record-header">
                                    <td class="row ps-0">
                                        <div class="col">
                                            <div class="record-value header-name renderjson" data-type="key">
                                                <span class="key">${header.key}</span>
                                            </div>
                                        </div>
                                    </td>
                                    <td class="pe-0">
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
        <div class="row g-2 value-row mb-2">
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
