<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicNames" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="clusterInfo" type="com.infobip.kafkistry.kafka.ClusterInfo" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/management/bulkReBalanceTopicsForm.js?ver=${lastCommit}"></script>
    <script src="static/regexInspector.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Topic re-assignment</title>
    <meta name="clusterIdentifier" content="${clusterIdentifier}">
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1><#include "../common/backBtn.ftl"> Topic bulk re-balance on kafka</h1>
    <hr>
    <#assign bulkReBalanceDoc = true>
    <#include "doc/bulkTopicReBalanceDoc.ftl">
    <h4>Choose criteria which topics to select to perform re-balance</h4>
    <br>

    <p><strong>Cluster</strong>: <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
    </p>

    <div class="form">

       <div class="form-row form-group">
            <label class="col-">Topic(s) name filter:</label>
            <div class="col">
                <div class="input-group">
                    <div class="input-group-append">
                        <select class="form-control" name="patternFilterType" title="Include/Exclude" data-style="alert-secondary">
                            <option value="INCLUDE" data-content="<span class='text-success'>Include</span>" selected></option>
                            <option value="EXCLUDE" data-content="<span class='text-danger'>Exclude</span>"></option>
                        </select>
                    </div>
                    <input class="form-control" name="topicNamePattern" type="text"
                           placeholder="enter optional /pattern/..." title="Topic name regex">
                </div>
            </div>
        </div>

        <div class="form-row form-group">
            <div class="col">
                <label>Topic count limit:</label>
                <input class="form-control" name="topicCountLimit" type="number" placeholder="optional max topics count..."
                       title="Maximum topics count to re-balance" value="10">
            </div>
            <div class="col">
                <label>Topic-partition count limit:</label>
                <input class="form-control" name="topicPartitionCountLimit" type="number" placeholder="optional max topic-partitions count..."
                       title="Maximum topic-partition count to re-assign" value="200">
            </div>
            <div class="col">
                <label>Migration data size limit:</label>
                <div class="input-group">
                    <input class="form-control" name="totalMigrationBytesLimit" type="number"
                           placeholder="optional max data migration..."
                           title="Maximum total data migration" value="20">
                    <div class="input-group-append">
                        <select class="form-control" data-style="alert-secondary" name="totalMigrationBytesLimit-unit" title="data unit">
                            <option>TB</option>
                            <option selected>GB</option>
                            <option>MB</option>
                            <option>kB</option>
                            <option>B</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>

        <div class="form-row form-group">
            <div class="col">
                <label>Topics to re-balance selection order:</label>
                <div class="input-group">
                    <div class="input-group-append">
                        <#assign defaultTopicSelectOrder = "TOP">
                        <select name="topicSelectOrder" class="form-control" data-style="alert-secondary" title="Topics select order">
                            <#assign topicSelectOrders = enums["com.infobip.kafkistry.service.topic.BulkReAssignmentOptions$TopicSelectOrder"]>
                            <#list topicSelectOrders as topicSelectOrder, enum>
                                <#assign selected = topicSelectOrder == defaultTopicSelectOrder>
                                <option value="${topicSelectOrder}" <#if selected>selected</#if>>${topicSelectOrder} BY</option>
                            </#list>
                        </select>
                    </div>
                    <#assign defaultTopicBy = "MIGRATION_BYTES">
                    <select name="topicBy" class="form-control" title="Topics select by">
                        <#assign topicByTypes = enums["com.infobip.kafkistry.service.topic.BulkReAssignmentOptions$TopicBy"]>
                        <#list topicByTypes as topicBy, enum>
                            <#assign selected = topicBy == defaultTopicBy>
                            <option <#if selected>selected</#if>>${topicBy}</option>
                        </#list>
                    </select>
                </div>
            </div>
            <div class="col">
                <label>Re-balance mode:</label>
                <div>
                    <#assign defaultReBalanceMode = "REPLICAS_THEN_LEADERS">
                    <select name="reBalanceMode" class="form-control" title="Re-balance mode">
                        <#assign reBalanceModes = enums["com.infobip.kafkistry.service.topic.ReBalanceMode"]>
                        <#list reBalanceModes as reBalanceMode, enum>
                            <#assign selected = reBalanceMode == defaultReBalanceMode>
                            <option <#if selected>selected</#if>>${reBalanceMode}</option>
                        </#list>
                    </select>
                </div>
            </div>
            <div class="col">
                <label>Excluded brokers:</label>
                <div>
                    <select name="excludedBrokerIds" class="form-control" title="Broker ids to exclude from assignments" multiple>
                        <#list clusterInfo.brokerIds as brokerId>
                            <option>${brokerId?c}</option>
                        </#list>
                    </select>
                </div>
            </div>
        </div>

    </div>

    <div class="form-row">
        <a id="bulkReBalanceUrl" class="btn btn-primary width-full" href="#">
            Propose re-assignments for re-balance
        </a>
    </div>

    <div id="topic-names" style="display: none;">
        <#list topicNames as topic>
            <div class="topic-name" data-topic-name="${topic}"></div>
        </#list>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>