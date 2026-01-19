<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="consumersData" type="com.infobip.kafkistry.service.consumers.AllConsumersData" -->
<#-- @ftlvariable name="consumersStats"  type="com.infobip.kafkistry.service.consumers.ConsumersStats" -->
<#-- @ftlvariable name="clustersGroups" type="java.util.List<com.infobip.kafkistry.service.consumers.ClusterConsumerGroup>" -->
<#-- @ftlvariable name="groupsOwned" type="java.util.Map<java.lang.String, java.lang.Boolean>" -->
<#-- @ftlvariable name="clusterIdentifiers" type="java.util.List<java.lang.String>" -->

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<div class="card">
    <div class="card-header collapsed" data-toggle="collapsing" data-bs-target="#cluster-pooling-statuses-body">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        <span class="h5">Cluster pooling statuses</span>
    </div>
    <div id="cluster-pooling-statuses-body" class="card-body collapseable p-1">
        <#list consumersData.clustersDataStatuses as clusterDataStatus>
            <#assign stateClass = util.levelToHtmlClass(clusterDataStatus.clusterStatus.level)>
            <#assign clusterTooltip>
                ${clusterDataStatus.clusterStatus}<br/>
                Last refresh before: ${(.now?long - clusterDataStatus.lastRefreshTime)/1000} sec
            </#assign>
            <div class="alert alert-sm alert-inline ${stateClass} cluster-status-badge status-filter-btn" role="alert"
                 data-status-type="${clusterDataStatus.clusterIdentifier}"
                 title="Click to filter by..." data-table-id="consumer-groups">
                ${clusterDataStatus.clusterIdentifier} <@info.icon tooltip=clusterTooltip/>
            </div>
        </#list>
    </div>
</div>
<div class="card">
    <div class="card-header" data-toggle="collapsing" data-bs-target="#statistics-counts-body">
        <span class="when-collapsed" title="expand...">▼</span>
        <span class="when-not-collapsed" title="collapse...">△</span>
        <span class="h5">Statistics / counts</span>
    </div>
    <div id="statistics-counts-body" class="card-body collapseable show p-1">
        <#include "../consumers/consumerGroupsCounts.ftl">
    </div>
</div>
<br/>

<div class="card">
    <div class="card-header">
        <div class="row">
            <div class="col">
                <span class="h4">All clusters consumers</span>
            </div>
            <div class="col-auto">
                <#assign presetDoc>
                    Use when need to prepare consumer group offsets to particular point
                    (<code>begin</code>, <code>end</code>, <code>@ timestamp</code>,...)
                    prior to first starting consumer so that it starts consuming at pre-specified offsets.<br/>
                    Might be useful for example in situation when consumer group is planned to have
                    <code>auto.offset.reset</code> = <code>earliest</code>, but for very first usage you'd like to
                    start from <code>latest</code> or <code>(now-1h)</code>-corresponding offset.
                </#assign>
                <div id="preset-group-form-btn" class="btn btn-outline-primary"
                     data-toggle="collapsing" data-bs-target="#preset-group-form">
                    Init-preset group... <@info.icon tooltip=presetDoc/>
                </div>
            </div>
        </div>
    </div>
    <div class="card-body pl-0 pr-0">
        <div class="row mb-3 collapseable" id="preset-group-form">
            <div class="col"></div>
            <div class="col">
                <div class="row g-2">
                    <div class="col m-1">
                        <select name="preset-cluster-identifier" class="selectpicker"
                            title="Custer Identifier">
                            <option disabled selected value="">Select cluster...</option>
                            <#list clusterIdentifiers as clusterIdentifier>
                                <option>${clusterIdentifier}</option>
                            </#list>
                        </select>
                    </div>
                </div>
                <div class="row g-2">
                    <div class="col m-1">
                        <input type="text" name="preset-consumer-group-id" class="form-control"
                               placeholder="Enter consumer group to init/preset offsets" title="Consumer group ID"/>
                    </div>
                </div>
                <div class="row g-2">
                    <div class="col m-1">
                        <a class="btn btn-sm btn-primary disabled"
                           id="init-consumer-group-btn"
                           data-base-href="${appUrl.consumerGroups().showPresetConsumerGroupOffsets("", "")}">
                            Continue to preset offsets...</a>
                    </div>
                </div>
                <hr/>
            </div>
        </div>
        <#assign datatableId = "consumer-groups">
        <#include "../common/loading.ftl">
        <table id="${datatableId}" class="table table-hover table-bordered datatable display" style="display: none;">
    <thead class="table-theme-dark">
    <tr>
        <th>Group</th>
        <th>Status</th>
        <th>Assignor</th>
        <th>Lag</th>
        <th>Topics</th>
    </tr>
    </thead>
    <#list clustersGroups as clusterGroup>
        <#assign consumerGroup = clusterGroup.consumerGroup>
        <#assign groupOwned = groupsOwned[consumerGroup.groupId]>
        <tr class="consumer-group-row"
            data-consumer-group-id="${consumerGroup.groupId}"
            data-cluster-identifier="${clusterGroup.clusterIdentifier}">
            <td data-order="${groupOwned?then("0", "1")}_${consumerGroup.groupId}_${clusterGroup.clusterIdentifier}">
                <a href="${appUrl.consumerGroups().showConsumerGroup(clusterGroup.clusterIdentifier, consumerGroup.groupId)}">
                    ${consumerGroup.groupId}
                    <span class="text-secondary">@</span>
                    ${clusterGroup.clusterIdentifier}
                </a>
                <#if groupOwned>
                    <@util.yourOwned what="consumer group"/>
                </#if>
            </td>
            <td>
                <@util.namedTypeStatusAlert type=consumerGroup.status/>
            </td>
            <td>
                ${consumerGroup.partitionAssignor}
            </td>
            <td>
                <@util.namedTypeStatusAlert type=consumerGroup.lag.status/>
            </td>
            <td>
                <span class="text-nowrap">
                    ${consumerGroup.topicMembers?size} topic(s)
                    <#assign topicsTooltip>
                        <table class='table table-sm'>
                            <thead class='thead-light'>
                            <tr>
                                <th><strong>Lag</strong></th>
                                <th class='text-center'><strong>Topic</strong></th>
                            </tr>
                            </thead>
                            <#list consumerGroup.topicMembers as topicMember>
                                <tr class='top-bordered'>
                                    <td>
                                        <#if (topicMember.lag.amount)??>
                                            ${util.prettyNumber(topicMember.lag.amount)}
                                        <#else>
                                            N/A
                                        </#if>
                                    <td>${topicMember.topicName}</td>
                                </tr>
                            </#list>
                        </table>
                    </#assign>
                    <@info.icon tooltip=topicsTooltip/>
                </span>
            </td>
        </tr>
    </#list>
</table>
    </div>
</div>
