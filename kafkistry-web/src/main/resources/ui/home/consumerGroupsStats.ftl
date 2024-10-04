<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="consumersStats"  type="com.infobip.kafkistry.service.consumers.ConsumersStats" -->
<#-- @ftlvariable name="ownedConsumersStats"  type="com.infobip.kafkistry.service.consumers.ConsumersStats" -->

<#import "../common/util.ftl" as util>
<#import "../consumers/util.ftl" as consumerUtil>

<#if consumersStats.consumerStatusCounts?size == 0>
    <div class="all-consumers-stats owned-consumer-stats">
        <div class="p-3">
            <span><i>(No consumer groups)</i></span>
        </div>
    </div>
<#else>
    <#macro consumerStatsTables cStats navigateSuffix="">
    <#-- @ftlvariable name="cStats"  type="com.infobip.kafkistry.service.consumers.ConsumersStats" -->
        <table class="table table-sm m-0">
            <thead class="thead-light">
            <tr>
                <th colspan="2">Group status</th>
            </tr>
            </thead>
            <#list cStats.consumerStatusCounts as stateType, count>
                <tr>
                    <td>
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${appUrl.consumerGroups().showAllClustersConsumerGroups()}#${stateType}${navigateSuffix}"
                           title="Click to filter consumer groups...">
                            <@util.namedTypeStatusAlert type=stateType alertInline=false/>
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
            <#list cStats.lagStatusCounts as stateType, count>
                <tr>
                    <td>
                        <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                           href="${appUrl.consumerGroups().showAllClustersConsumerGroups()}#${stateType}${navigateSuffix}"
                           title="Click to filter consumer groups...">
                            <@util.namedTypeStatusAlert type=stateType alertInline=false/>
                        </a>
                    </td>
                    <td class="text-right">${count}</td>
                </tr>
            </#list>
        </table>
    </#macro>

    <div class="all-consumers-stats">
        <@consumerStatsTables cStats=consumersStats/>
    </div>
    <div class="owned-consumers-stats">
        <#if ownedConsumersStats.consumerStatusCounts?size == 0>
            <div class="p-2">
                <i>(no consumer groups in your ownership)</i>
            </div>
        <#else>
            <@consumerStatsTables cStats=ownedConsumersStats navigateSuffix="%20YOUR"/>
        </#if>
    </div>
</#if>
