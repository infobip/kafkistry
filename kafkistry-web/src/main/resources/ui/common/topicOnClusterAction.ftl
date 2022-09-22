<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="availableActions"  type="java.util.List<com.infobip.kafkistry.service.topic.AvailableAction>" -->
<#-- @ftlvariable name="topicName"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->

<#import "documentation.ftl" as doc>
<#import "infoIcon.ftl" as info>

<#list availableActions as action>
    <#switch action>
        <#case "CREATE_TOPIC">
            <#assign url = appUrl.topicsManagement().showCreateMissingTopic(topicName, clusterIdentifier)>
            <#assign btnText>
                Create topic on kafka... <@info.icon tooltip=doc.createTopicOnKafkaBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-primary btn-sm mb-1">${btnText}</button></a>
            <#break>
        <#case "SUGGESTED_EDIT">
            <#assign url = appUrl.topics().showSuggestEditTopic(topicName)>
            <#assign btnText>
                Suggested edit... <@info.icon tooltip=doc.suggestedEditBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-primary btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "MANUAL_EDIT">
            <#assign url = appUrl.topics().showEditTopic(topicName)>
            <#assign btnText>
                Manual edit... <@info.icon tooltip=doc.manualEditBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-primary btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "FIX_VIOLATIONS_EDIT">
            <#assign url = appUrl.topics().showFixViolationsEdit(topicName)>
            <#assign btnText>
                Fix violations edit... <@info.icon tooltip=doc.fixViolationsEditBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-primary btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "DELETE_TOPIC_ON_KAFKA">
            <#assign url = appUrl.topicsManagement().showDeleteTopicOnCluster(topicName, clusterIdentifier)>
            <#assign btnText>
                Delete topic on kafka... <@info.icon tooltip=doc.deleteClusterTopicBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-danger btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "IMPORT_TOPIC">
            <#assign url = appUrl.topics().showImportTopic(topicName)>
            <#assign btnText>
                Import topic... <@info.icon tooltip=doc.importTopicBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-primary btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "ALTER_TOPIC_CONFIG">
            <#assign url = appUrl.topicsManagement().showTopicConfigUpdate(topicName, clusterIdentifier)>
            <#assign btnText>
                Update topic config... <@info.icon tooltip=doc.alterTopicConfigBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-warning btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "ALTER_PARTITION_COUNT">
            <#assign url = appUrl.topicsManagement().showTopicPartitionCountChange(topicName, clusterIdentifier)>
            <#assign btnText>
                Apply partition count change... <@info.icon tooltip=doc.alterTopicPartitionCountBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-warning btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "ALTER_REPLICATION_FACTOR">
            <#assign url = appUrl.topicsManagement().showTopicReplicationFactorChange(topicName, clusterIdentifier)>
            <#assign btnText>
                Apply replication factor change... <@info.icon tooltip=doc.alterTopicReplicationFactorBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-warning btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "RE_BALANCE_ASSIGNMENTS">
            <#assign url = appUrl.topicsManagement().showTopicReBalance(topicName, clusterIdentifier, "REPLICAS_THEN_LEADERS")>
            <#assign btnText>
                Re-balance replica assignments... <@info.icon tooltip=doc.reBalanceTopicReplicaAssignments/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-warning btn-sm m-1">${btnText}</button></a>
            <#break>
        <#case "INSPECT_TOPIC">
            <#assign url = appUrl.topics().showInspectTopicOnCluster(topicName, clusterIdentifier)>
            <#assign btnText>
                Show topic assignment inspection... <@info.icon tooltip=doc.showTopicInspectionBtn/>
            </#assign>
            <a href="${url}"><button class="text-nowrap btn btn-outline-info btn-sm m-1">${btnText}</button></a>
            <#break>
    </#switch>
</#list>
<#if availableActions?size == 0>
    ------
</#if>
