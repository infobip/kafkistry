<#-- @ftlvariable name="topicDescription" type="com.infobip.kafkistry.model.TopicDescription" -->

<#if topicDescription.allowManualProduce??>
    <#if topicDescription.allowManualProduce>
        <span class="badge badge-success">Allowed</span>
    <#else>
        <span class="badge badge-danger">Denied</span>
    </#if>
<#else>
    <span class="badge badge-secondary">Default</span>
    <span class="text-muted small">(use global setting)</span>
</#if>
