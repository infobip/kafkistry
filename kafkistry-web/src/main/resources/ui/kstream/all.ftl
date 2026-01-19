<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterKStreamApps" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.kafkastreams.KafkaStreamsApp>" -->

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

    <div class="card">
        <div class="card-header">
            <span class="h4">All detected KStream apps</span>
        </div>
        <div class="card-body pl-0 pr-0">
            <table class="table table-hover table-bordered datatable display">
                <thead class="table-theme-dark">
                <tr>
                    <th>App ID on cluster</th>
                    <th>#input topics</th>
                    <th>#internal topics</th>
                </tr>
                </thead>
                <#list clusterKStreamApps as clusterIdentifier, kafkaStreamsApps>
                    <#list kafkaStreamsApps as kafkaStreamsApp>
                        <tr>
                            <td>
                                <a href="${appUrl.kStream().showKStreamApp(clusterIdentifier, kafkaStreamsApp.kafkaStreamAppId)}"
                                   title="Inspect this group KStreams app">
                                    ${kafkaStreamsApp.kafkaStreamAppId}
                                    <span class="text-secondary">@</span>
                                    ${clusterIdentifier}
                                </a>
                            </td>
                            <td>
                                <#assign inputTopicsTooltip>
                                    <ul>
                                        <#list kafkaStreamsApp.inputTopics as inputTopic>
                                            <li>${inputTopic}</li>
                                        </#list>
                                    </ul>
                                </#assign>
                                ${kafkaStreamsApp.inputTopics?size}
                                <@info.icon tooltip=inputTopicsTooltip/>
                                <div style="display: none;">${inputTopicsTooltip}</div> <!-- searchable -->
                            </td>
                            <td>
                                <#assign internalTopicsTooltip>
                                    <ul>
                                        <#list kafkaStreamsApp.getKStreamInternalTopics() as internalTopic>
                                            <li>${internalTopic}</li>
                                        </#list>
                                    </ul>
                                </#assign>
                                ${kafkaStreamsApp.getKStreamInternalTopics()?size}
                                <@info.icon tooltip=internalTopicsTooltip/>
                                <div style="display: none;">${internalTopicsTooltip}</div>  <!-- searchable -->
                            </td>
                        </tr>
                    </#list>
                </#list>
            </table>
        </div>
    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
