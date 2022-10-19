<#import "../common/infoIcon.ftl" as cglInfo>

<#macro lagDoc>
    <#assign lagDocTooltip>
        Difference between topic's log end (latest) offset and consumer group's most recently committed offset.
        Lag is number of messages consumer still needs to process before reaching end of topic partition.
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