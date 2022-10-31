<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#function enumListToStringList enumList>
<#-- @ftlvariable name="enumList" type="java.util.List<? extends java.lang.Enum>" -->
    <#assign result = []>
    <#list enumList as enum>
        <#assign result = result + [enum.name()]>
    </#list>
    <#return result>
</#function>

<#function namedTypeListToStringList namedTypesList>
<#-- @ftlvariable name="namedTypesList" type="java.util.List<? extends com.infobip.kafkistry.service.NamedType>" -->
    <#assign result = []>
    <#list namedTypesList as namedType>
        <#assign result = result + [namedType.name]>
    </#list>
    <#return result>
</#function>

<#function levelToHtmlClass level>
<#-- @ftlvariable name="level"  type="com.infobip.kafkistry.service.StatusLevel" -->
    <#switch level>
        <#case "SUCCESS">
            <#return "alert-success">
        <#case "IMPORTANT">
            <#return "alert-primary">
        <#case "IGNORE">
            <#return "alert-secondary">
        <#case "INFO">
            <#return "alert-info">
        <#case "WARNING">
            <#return "alert-warning">
        <#case "ERROR">
            <#return "alert-danger">
        <#case "CRITICAL">
            <#return "bg-danger text-white">
        <#default >
            <#return "">
    </#switch>
</#function>

<#function clusterStatusFlags statusPerClusters>
<#-- @ftlvariable name="statusPerClusters"  type="java.util.List<com.infobip.kafkistry.service.topic.TopicClusterStatus>" -->
    <#assign clusterFlags = {}>
    <#list statusPerClusters as clusterStatus>
        <#assign clusterFlags = clusterFlags + {clusterStatus.clusterIdentifier: clusterStatus.status.flags}>
    </#list>
    <#return clusterFlags>
</#function>

<#function nonOkTopicStatusTypes statusPerClusters>
<#-- @ftlvariable name="statusPerClusters"  type="java.util.List<com.infobip.kafkistry.service.topic.TopicClusterStatus>" -->
    <#assign allTypes = []>
    <#list statusPerClusters as clusterStatus>
        <#list clusterStatus.status.types as statusType>
            <#if !statusType.category.ok && !allTypes?seq_contains(statusType)>
                <#assign allTypes = allTypes + [statusType]>
            </#if>
        </#list>
    </#list>
    <#return allTypes>
</#function>

<#function allTopicStatusTypes statusPerClusters>
<#-- @ftlvariable name="statusPerClusters"  type="java.util.List<com.infobip.kafkistry.service.topic.TopicClusterStatus>" -->
    <#assign allTypes = []>
    <#list statusPerClusters as clusterStatus>
        <#list clusterStatus.status.types as statusType>
            <#if !allTypes?seq_contains(statusType)>
                <#assign allTypes = allTypes + [statusType]>
            </#if>
        </#list>
    </#list>
    <#return allTypes>
</#function>

<#macro newTag>
    <span class="bg-danger text-light" style="border-radius: 3px; padding: 2px; font-size: 7px; font-weight: bold;">
      NEW
    </span>
</#macro>

<#function numberToString number ensureSign=false>
<#-- @ftlvariable name="number"  type="java.lang.Number" -->
    <#if number?is_nan>
        <#return "NaN">
    </#if>
    <#if ensureSign && number gt 0>
        <#return "+" + numberToString(number)>
    </#if>
    <#if number < 0>
        <#return "-" + numberToString(-number)>
    </#if>
    <#return "${number}">
</#function>

<#function prettyNumber number ensureSign=false>
<#-- @ftlvariable name="number"  type="java.lang.Number" -->
    <#if number?is_nan>
        <#return "NaN">
    </#if>
    <#if ensureSign && number gt 0>
        <#return "+" + prettyNumber(number)>
    </#if>
    <#if number < 0>
        <#return "-" + prettyNumber(-number)>
    </#if>
    <#if number?is_infinite>
        <#return "Inf">
    </#if>
    <#if number < 1000>
        <#if number == 0>
            <#return "0">
        <#elseif number == number?round>
            <#return "${number}">
        <#elseif number < 0.005>
            <#return ">#{number; m1M2}">
        <#else>
            <#return "#{number; m1M2}">
        </#if>
    </#if>
    <#assign numK = number / 1000>
    <#if numK < 1000>
        <#return "#{numK; m1M2}k">
    </#if>
    <#assign numM = numK / 1000>
    <#if numM < 1000>
        <#return "#{numM; m1M2}M">
    </#if>
    <#assign numG = numM / 1000>
    <#return "#{numG; m1M2}G">
</#function>

<#function prettyDataSize number ensureSign=false>
<#-- @ftlvariable name="number"  type="java.lang.Number" -->
    <#if ensureSign && number gt 0>
        <#return "+" + prettyDataSize(number)>
    </#if>

    <#if number < 0>
        <#return "-" + prettyDataSize(-number)>
    </#if>
    <#if number < 1024>
        <#if number == number?round>
            <#return "${number}B">
        <#else>
            <#return "#{number; m1M2}B">
        </#if>
    </#if>
    <#assign numK = number / 1024>
    <#if numK < 1024>
        <#return "#{numK; m1M2}kB">
    </#if>
    <#assign numM = numK / 1024>
    <#if numM < 1024>
        <#return "#{numM; m1M2}MB">
    </#if>
    <#assign numG = numM / 1024>
    <#if numG < 1024>
        <#return "#{numG; m1M2}GB">
    </#if>
    <#assign numT = numG / 1024>
    <#if numT < 1024>
        <#return "#{numT; m1M2}TB">
    </#if>
    <#assign numY = numT / 1024>
    <#return "#{numY; m1M2}YB">
</#function>

<#function prettyDuration secs ensureSign=false>
    <#if ensureSign && secs gt 0>
        <#return "+" + prettyDuration(secs)>
    </#if>

    <#if secs < 0>
        <#return "-" + prettyDuration(-secs)>
    </#if>
    <#if secs < 1>
        <#assign ms = secs * 1000.0>
        <#return "#{ms; m0M2} ms">
    </#if>
    <#if secs < 120>
        <#return "#{secs; m1M2} sec(s)">
    </#if>
    <#assign mins = secs / 60>
    <#if mins < 120>
        <#return "#{mins; m1M2} min(s)">
    </#if>
    <#assign hours = mins / 60>
    <#if hours < 48>
        <#return "#{hours; m1M2} hour(s)">
    </#if>
    <#assign days = hours / 24>
    <#return "#{days; m1M2} day(s)">
</#function>

<#function commitHashUrl commitId gitCommitBaseUrl, gitEmbeddedBrowse>
<#-- @ftlvariable name="commitId"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
    <#if gitCommitBaseUrl?length gt 0>
        <#assign commitUrl = gitCommitBaseUrl + commitId>
        <#assign link>
            <a <#if !gitEmbeddedBrowse>target="_blank"</#if> href="${commitUrl}">${commitId?substring(0, 8)}</a>
        </#assign>
        <#return link>
    </#if>
    <#return commitId?substring(0, 8)>
</#function>

<#function changeTypeClass changeType>
<#-- @ftlvariable name="changeType"  type="com.infobip.kafkistry.repository.storage.ChangeType" -->
    <#switch changeType>
        <#case "ADD">
            <#return "badge-success">
        <#case "DELETE">
            <#return "badge-danger">
        <#case "UPDATE">
            <#return "badge-primary">
    </#switch>
    <#return "badge-secondary">
</#function>

<#function presenceTypeClass presenceType>
<#-- @ftlvariable name="presenceType"  type="com.infobip.kafkistry.model.PresenceType" -->
    <#switch presenceType>
        <#case "ALL_CLUSTERS">
            <#return "alert-primary">
        <#case "INCLUDED_CLUSTERS">
            <#return "alert-success">
        <#case "EXCLUDED_CLUSTERS">
            <#return "alert-danger">
        <#case "TAGGED_CLUSTERS">
            <#return "alert-info">
    </#switch>
    <#return "alert-secondary">
</#function>

<#macro presence presence inline = true>
<#-- @ftlvariable name="presence"  type="com.infobip.kafkistry.model.Presence" -->
    <div class="alert alert-sm alert-inline m-1 ${presenceTypeClass(presence.type)}">
        ${presence.type.name()?replace("_", " ")}
    </div>
    <#if (presence.kafkaClusterIdentifiers)??>
        <#if !inline><br/></#if>
        <#list presence.kafkaClusterIdentifiers as cluster>
            <small>${cluster}</small><#if !cluster?is_last>,</#if>
        </#list>
    </#if>
    <#if (presence.tag)??>
        <#if !inline><br/></#if>
        <span class="badge badge-secondary">${presence.tag}</span>
    </#if>
</#macro>

<#macro ok ok>
<#-- @ftlvariable name="ok" type="java.lang.Boolean" -->
    <div role="alert" class="alert alert-inline alert-sm ${ok?then("alert-success", "alert-danger")} mb-0">
        ${ok?then("YES", "NO")}
    </div>
</#macro>

<#macro inRegistry flag>
<#-- @ftlvariable name="flag" type="java.lang.Boolean" -->
    <span class="badge ${flag?then("badge-success", "badge-danger")}">
        ${flag?then("YES", "NO")}
    </span>
</#macro>

<#macro namedTypeStatusAlert type alertInline=true small=false>
<#-- @ftlvariable name="type" type="com.infobip.kafkistry.service.NamedType" -->
    <#import "infoIcon.ftl" as infoForAlert>
    <div role="alert" class="alert text-nowrap
        <#if alertInline>alert-inline mb-1<#else>mb-0</#if>
        alert-sm ${levelToHtmlClass(type.level)}
        <#if small>mb-0 p-0 pr-1 pl-1</#if>
        "
    >
        ${type?api.getName()} <#-- workaround for to work both for enums and normal data classes -->
        <#assign tooltip>
            <#if type.valid>
                <span class='badge badge-success'>VALID</span>
            <#else>
                <span class='badge badge-danger'>INVALID</span>
            </#if>
            ${type.doc}
            <#if (type.getClass().simpleName?starts_with("Topic"))>
                <#assign topicStatusType = type>
                <#-- @ftlvariable name="topicStatusType" type="com.infobip.kafkistry.service.topic.TopicInspectionResultType" -->
                <br/>
                <span>
                    <strong>Issue category:</strong> <span class='text-info'>${topicStatusType.category}</span>
                </span>
            </#if>
        </#assign>
        <@infoForAlert.icon tooltip=tooltip/>
    </div>
</#macro>

<#function usageLevelToHtmlClass level>
<#-- @ftlvariable name="level"  type="com.infobip.kafkistry.service.resources.UsageLevel" -->
    <#switch level>
        <#case "LOW">
            <#return "alert-success">
        <#case "MEDIUM">
            <#return "alert-warning">
        <#case "HIGH">
            <#return "alert-danger">
        <#case "OVERFLOW">
            <#return "bg-danger text-white">
        <#default>
            <#return "">
    </#switch>
    <#return "">
</#function>


