<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="kafkaStreamsApp" type="com.infobip.kafkistry.service.kafkastreams.KafkaStreamsApp" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: KStream app</title>
    <meta name="current-nav" content="nav-kstream"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h3><#include  "../common/backBtn.ftl"> KStream application info</h3>

    <table class="table table-hover">
        <tr>
            <th>Cluster</th>
            <td><a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a></td>
        </tr>
        <tr>
            <th>KStream application ID</th>
            <td>
                <code>${kafkaStreamsApp.kafkaStreamAppId}</code>
            </td>
        <tr>
            <th>Consumer group</th>
            <td>
                <a href="${appUrl.consumerGroups().showConsumerGroup(clusterIdentifier, kafkaStreamsApp.kafkaStreamAppId)}"
                   title="Inspect this group KStreams app">
                        ${kafkaStreamsApp.kafkaStreamAppId}
                </a>
            </td>
        </tr>
    </table>

    <div class="card">
        <div class="card-header">
            <h4>Involved topics</h4>
        </div>
        <div class="card-body p-0">
            <table class="table table-hover m-0">
                <tr class="table-theme-dark">
                    <th>Topic</th>
                    <th>Involvement</th>
                </tr>
                <#list kafkaStreamsApp.inputTopics as inputTopic>
                    <tr>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(inputTopic, clusterIdentifier)}">
                                ${inputTopic}
                            </a>
                        </td>
                        <td><span class="badge bg-primary">INPUT</span></td>
                    </tr>
                </#list>
                <#list kafkaStreamsApp.getKStreamInternalTopics() as internalTopic>
                    <tr>
                        <td>
                            <a href="${appUrl.topics().showInspectTopicOnCluster(internalTopic, clusterIdentifier)}">
                                ${internalTopic}
                            </a>
                        </td>
                        <td><span class="badge bg-info">INTERNAL</span></td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>