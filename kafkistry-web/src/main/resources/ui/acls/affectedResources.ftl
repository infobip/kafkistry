<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="ruleStatus" type="com.infobip.kafkistry.service.AclRuleStatus" -->

<#macro affectedTopic cluster, topic>
    <a href="${appUrl.topics().showInspectTopicOnCluster(topic, cluster)}">
        <button class="btn btn-sm btn-outline-dark mb-1"
                title="Inspect this topic on this cluster...">
            ${topic} 🔍
        </button>
    </a>
</#macro>

<#macro affectedConsumerGroup cluster, groupId>
    <a href="${appUrl.consumerGroups().showConsumerGroup(cluster, groupId)}">
        <button class="btn btn-sm btn-outline-dark mb-1"
                title="Inspect this consumer group on this cluster...">
            ${groupId} 🔍
        </button>
    </a>
</#macro>

<#if (ruleStatus.affectedTopics?size gt 0) || (ruleStatus.affectedConsumerGroups?size gt 0)>
    <div style="max-height: 520px !important; overflow-y: scroll;">

        <#if ruleStatus.affectedTopics?size == 1>
            <@affectedTopic cluster=clusterIdentifier topic=ruleStatus.affectedTopics[0]/>
        <#elseif ruleStatus.affectedTopics?size gt 1>
            <div class="collapsed" data-target=".affected-topics-${clusterIdentifier}" data-toggle="collapsing">
                <div class="p-2">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    <span class="message">${ruleStatus.affectedTopics?size} topics...</span>
                </div>
                <div class="affected-topics-${clusterIdentifier} collapseable">
                    <#list ruleStatus.affectedTopics as topic>
                        <@affectedTopic cluster=clusterIdentifier topic=topic/><br/>
                    </#list>
                </div>
            </div>
        </#if>

        <#if ruleStatus.affectedConsumerGroups?size == 1>
            <@affectedConsumerGroup cluster=clusterIdentifier groupId=ruleStatus.affectedConsumerGroups[0]/>
        <#elseif ruleStatus.affectedConsumerGroups?size gt 1>
            <div class="collapsed" data-target=".affected-groups-${clusterIdentifier}" data-toggle="collapsing">
                <div class="p-2">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    <span class="message">${ruleStatus.affectedConsumerGroups?size} consumer groups...</span>
                </div>
                <div class="affected-groups-${clusterIdentifier} collapseable">
                    <#list ruleStatus.affectedConsumerGroups as groupId>
                        <@affectedConsumerGroup cluster=clusterIdentifier groupId=groupId/><br/>
                    </#list>
                </div>
            </div>
        </#if>

    </div>
<#else>
    ---
</#if>
