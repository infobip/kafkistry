<#import "../common/infoIcon.ftl" as cglInfo>

<#function alertClassFor value type>
<#-- @ftlvariable name="value" type="java.lang.Object" -->
<#-- @ftlvariable name="type" type="java.lang.String" -->
    <#switch type>
        <#case "cluster">
            <#return "alert-primary">
        <#case "lag">
            <#return lagStatusAlertClass(value)>
        <#case "consumer">
            <#return consumerStatusAlertClass(value)>
    </#switch>
    <#return "alert-secondary">
</#function>

<#function consumerStatusAlertClass status>
    <#switch status>
        <#case "STABLE">
            <#return "alert-success">
        <#case "UNKNOWN">
            <#return "alert-warning">
        <#case "EMPTY">
            <#return "alert-warning">
        <#case "DEAD">
            <#return "alert-danger">
        <#case "PREPARING_REBALANCE">
        <#case "COMPLETING_REBALANCE">
            <#return "alert-info">
    </#switch>
    <#return "alert-secondary">
</#function>

<#function lagStatusAlertClass lag>
    <#switch lag>
        <#case "NO_LAG">
            <#return "alert-success">
        <#case "UNKNOWN">
            <#return "alert-warning">
        <#case "MINOR_LAG">
            <#return "alert-warning">
        <#case "HAS_LAG">
            <#return "alert-danger">
        <#case "OVERFLOW">
            <#return "bg-danger">
    </#switch>
    <#return "alert-secondary">
</#function>

<#macro lagDoc>
    <#assign lagDocTooltip>
        Difference between topic's log end (latest) offset and consumer group's most recently commited offset.
        Lag is number of messages consumer still needs to process before reachinhg end of topic partiton.
    </#assign>
    <@cglInfo.icon tooltip=lagDocTooltip/>
</#macro>

<#macro lagPercentDoc>
    <#assign lagPercentDocTooltip>
        Percentage representing how big is lag in terms of size of partition.<br/>
        <ul>
            <li><code>0%</code> &rarr; no lag at all</li>
            <li><code>100%</code> &rarr; lag is big as partition is</li>
            <li><code>&gt; 100%</code> &rarr; consumer overflowed past earliest record</li>
            <li><code>(inf)</code> &rarr; there is some lag but partition is empty</li>
        </ul>
    </#assign>
    <@cglInfo.icon tooltip=lagPercentDocTooltip/>
</#macro>