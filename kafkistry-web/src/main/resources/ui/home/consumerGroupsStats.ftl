<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="consumersStats"  type="com.infobip.kafkistry.service.consumers.ConsumersStats" -->

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumerUtil>

<#if consumersStats.consumerStatusCounts?size == 0>
    <div class="p-3">
        <span><i>(No consumer groups)</i></span>
    </div>
<#else>
    <table class="table table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th colspan="2">Group status</th>
        </tr>
        </thead>
        <#list consumersStats.consumerStatusCounts as stateType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.consumerGroups().showAllClustersConsumerGroups()}#${stateType}"
                       title="Click to filter consumer groups...">
                        <#assign stateClass = consumerUtil.consumerStatusAlertClass(stateType)>
                        <div class="alert alert-sm ${stateClass} mb-0">
                            ${stateType}
                        </div>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
    <table class="table table-sm m-0">
        <thead class="thead-light">
        <tr>
            <th colspan="2">Lag status</th>
        </tr>
        </thead>
        <#list consumersStats.lagStatusCounts as stateType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.consumerGroups().showAllClustersConsumerGroups()}#${stateType}"
                       title="Click to filter consumer groups...">
                        <@util.namedTypeStatusAlert type=stateType alertInline=false/>
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
