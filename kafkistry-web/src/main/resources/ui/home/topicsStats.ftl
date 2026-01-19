<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicsStats"  type="java.util.Map<com.infobip.kafkistry.service.topic.TopicInspectionResultType, java.lang.Integer>" -->
<#-- @ftlvariable name="ownedTopicsStats"  type="java.util.Map<com.infobip.kafkistry.service.topic.TopicInspectionResultType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if topicsStats?size == 0>
    <div class="all-topics-stats owned-topics-stats">
        <div class="p-3">
            <button type="button" class="btn btn-sm btn-primary dropdown-toggle" data-bs-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
               Add topic...
            </button>
            <div class="dropdown-menu">
                <a class="dropdown-item" href="${appUrl.topics().showTopicWizard()}">Wizard</a>
                <a class="dropdown-item" href="${appUrl.topics().showTopicCreate()}">Manual setup</a>
            </div>
        </div>
    </div>
<#else>
    <#macro topicsStatsTable tStats navigateSuffix="">
        <#-- @ftlvariable name="tStats"  type="java.util.Map<com.infobip.kafkistry.service.topic.TopicInspectionResultType, java.lang.Integer>" -->
        <table class="table table-hover table-sm m-0">
            <#list tStats as statusType, count>
                <tr>
                    <td>
                        <a class="m-0 p-0 width-full btn btn-sm text-start"
                           href="${appUrl.topics().showTopics()}#${statusType.name}${navigateSuffix}" title="Click to filter topics...">
                            <@util.namedTypeStatusAlert type = statusType alertInline=false/>
                        </a>
                    </td>
                    <td class="text-end align-content-center pe-2">${count}</td>
                </tr>
            </#list>
        </table>
    </#macro>
    <div class="all-topics-stats">
        <@topicsStatsTable tStats=topicsStats/>
    </div>
    <div class="owned-topics-stats">
        <#if ownedTopicsStats?size == 0>
            <div class="p-2">
                <i>(no topics in your ownership)</i>
            </div>
        <#else>
            <@topicsStatsTable tStats=ownedTopicsStats navigateSuffix="%20YOUR"/>
        </#if>
    </div>
</#if>
