<#-- @ftlvariable name="clusterGroups"  type="com.infobip.kafkistry.service.consumers.ClusterConsumerGroups" -->

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumerUtil>

<#assign consumersStats = clusterGroups.consumersStats>

<#if consumersStats.consumerStatusCounts?size == 0>
    <div class="p-3">
        <span><i>(No consumer groups)</i></span>
    </div>
<#else>
    <div class="row">
        <div class="col-4">
            <table class="table table-sm m-0">
                <thead class="thead-light">
                <tr>
                    <th colspan="2">Group status</th>
                </tr>
                </thead>
                <#list consumersStats.consumerStatusCounts as stateType, count>
                    <tr>
                        <td class="status-filter-btn agg-count-status-type" data-status-type="${stateType}"
                            title="Click to filter by..." data-table-id="consumer-groups-table">
                            <#assign stateClass = consumerUtil.alertClassFor(stateType, "consumer")>
                            <div class="alert alert-sm ${stateClass} mb-0">
                                ${stateType}
                            </div>
                        </td>
                        <td style="text-align: right;">${count}</td>
                    </tr>
                </#list>
            </table>
        </div>
        <div class="col-4">
            <table class="table table-sm m-0">
                <thead class="thead-light">
                <tr>
                    <th colspan="2">Lag status</th>
                </tr>
                </thead>
                <#list consumersStats.lagStatusCounts as stateType, count>
                    <tr>
                        <td class="status-filter-btn agg-count-status-type" data-status-type="${stateType}"
                            title="Click to filter by..." data-table-id="consumer-groups-table">
                            <#assign stateClass = consumerUtil.alertClassFor(stateType, "lag")>
                            <div class="alert alert-sm ${stateClass} mb-0">
                                ${stateType}
                            </div>
                        </td>
                        <td style="text-align: right;">${count}</td>
                    </tr>
                </#list>
            </table>
        </div>
        <div class="col-4">
            <table class="table table-sm m-0">
                <thead class="thead-light">
                <tr>
                    <th colspan="2">Partition assignor</th>
                </tr>
                </thead>
                <#list consumersStats.partitionAssignorCounts as stateType, count>
                    <tr>
                        <td class="status-filter-btn agg-count-status-type" data-status-type="${stateType}"
                            title="Click to filter by..." data-table-id="consumer-groups-table">
                            <div class="alert alert-sm alert-secondary mb-0">
                                ${stateType}
                            </div>
                        </td>
                        <td style="text-align: right;">${count}</td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
</#if>

