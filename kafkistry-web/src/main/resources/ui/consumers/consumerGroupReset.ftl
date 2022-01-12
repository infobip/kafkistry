<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroupId" type="java.lang.String" -->
<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer group</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/resetConsumer.js?ver=${lastCommit}"></script>
    <script src="static/consumer/resetForm.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<style>
    td {
        width: auto;
    }

    td.min {
        width: 1%;
        white-space: nowrap;
    }

    .legend-highlight {
        color: red;
        background-color: #b3d5ae;
    }

</style>
<div class="container">
    <h3><#include  "../common/backBtn.ftl"> Consumer group offset reset</h3>

    <#include "components/groupMetadata.ftl">

    <#if consumerGroup??>
        <div class="alert alert-info">
            <strong>NOTE:</strong>
            All instances of consumers must not be active while performing offset reset
        </div>
        <div class="card">
            <div class="card-header">
                <div class="h4 m-0">Choose reset options</div>
            </div>
            <div class="card-body">
                <select name="resetType">
                    <option value="EARLIEST">To begining (earliest)</option>
                    <option value="LATEST">To end (latest)</option>
                    <option value="TIMESTAMP">At timestamp</option>
                    <option value="RELATIVE">Relative seek</option>
                    <option value="EXPLICIT">To offset</option>
                </select>
                <label id="begin-option" class="reset-option" style="display: none;">
                    plus <input type="number" name="begin-offset" value="0">
                </label>
                <label id="end-option" class="reset-option" style="display: none;">
                    minus <input type="number" name="end-offset" value="0">
                </label>
                <label id="timestamp-option" class="reset-option" style="display: none;">
                    <input type="number" name="timestamp">
                    <i>(seek to offset of first record with greater or equal timestamp, NOTE: using your browser timezone in date/time picker)</i>
                </label>
                <label id="relative-option" class="reset-option" style="display: none;">
                    current consumer position shifted by
                    <input type="number" name="relative-offset" value="0">
                    <i>(positive=skip forward, negative=rewind backward)</i>
                </label>
                <label id="explicit-option" class="reset-option" style="display: none;">
                    with offset value of <input type="number" name="explicit-offset">
                </label>
                <div>
                    <table class="table table-borderless table-sm small mt-4 mb-0">
                        <tr class="no-hover">
                            <th colspan="10">Help legend:</th>
                        </tr>
                        <tr class="no-hover">
                            <td class="min text-left type type-begin">begin</td>
                            <td class="min text-left type type-begin-n">begin+N</td>
                            <td></td>
                            <td class="min text-center type type-explicit">Explicit</td>
                            <td></td>
                            <td class="min text-center type type-timestamp">&gt;=Timestamp</td>
                            <td></td>
                            <td class="min text-center type type-current-mn">current-N</td>
                            <td class="min text-center type type-current">current</td>
                            <td class="min text-center type type-current-pn">current+N</td>
                            <td></td>
                            <td class="min text-right type type-end-n">end-N</td>
                            <td class="min text-right type type-end">end</td>
                        </tr>
                        <tr class="bg-warning">
                            <td class="text-left type type-begin">|</td>
                            <td class="text-left type type-begin-n">|</td>
                            <td></td>
                            <td class="text-center type type-explicit">|</td>
                            <td></td>
                            <td class="text-center type type-timestamp">|</td>
                            <td></td>
                            <td class="text-center type type-current-mn">|</td>
                            <td class="text-center type type-current">|</td>
                            <td class="text-center type type-current-pn">|</td>
                            <td></td>
                            <td class="text-right type type-end-n">|</td>
                            <td class="text-right type type-end">|</td>
                        </tr>
                    </table>
                </div>
            </div>
        </div>
        <br/>
        <div class="card">
            <div class="card-header">
                <div class="h4 m-0">Select topics/partitions to reset</div>
            </div>
            <div class="card-body p-0">
                <#include "components/groupTopicPartitions.ftl">
            </div>
        </div>
        <br/>
        <#if consumerGroup.status != "EMPTY" && consumerGroup.status != "UNKNOWN">
            <div class="alert alert-warning">
                <strong>WARNING:</strong>
                It appears that consumer group is still active, status: ${consumerGroup.status}. Reset won't work.
            </div>
        </#if>

        <#include "../common/cancelBtn.ftl">
        <button id="reset-offsets-btn" class="btn btn-sm btn-primary"
                data-cluster="${clusterIdentifier}" data-consumer-group="${consumerGroupId}">
            Reset selected offsets
        </button>

        <#include "../common/serverOpStatus.ftl">
    </#if>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>