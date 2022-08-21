<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="topicsStats"  type="java.util.Map<com.infobip.kafkistry.service.topic.TopicInspectionResultType, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>

<#if topicsStats?size == 0>
    <div class="p-3">
        <button type="button" class="btn btn-sm btn-primary dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
           Add topic...
        </button>
        <div class="dropdown-menu open">
            <a class="dropdown-item" href="${appUrl.topics().showTopicWizard()}">Wizard</a>
            <a class="dropdown-item" href="${appUrl.topics().showTopicCreate()}">Manual setup</a>
        </div>
    </div>
<#else>
    <table class="table table-sm m-0">
        <#list topicsStats as statusType, count>
            <tr>
                <td>
                    <a class="m-0 p-0 width-full btn btn-sm btn-outline-light text-left"
                       href="${appUrl.topics().showTopics()}#${statusType.name}" title="Click to filter topics...">
                        <#assign asStatusFlag = true>
                        <#include "../common/topicStatusResultBox.ftl">
                    </a>
                </td>
                <td class="text-right">${count}</td>
            </tr>
        </#list>
    </table>
</#if>
