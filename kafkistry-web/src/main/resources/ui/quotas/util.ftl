
<#macro entity entity>
<#-- @ftlvariable name="entity" type="com.infobip.kafkistry.model.QuotaEntity" -->
    <#if !entity.userIsAny()>
        <span class="font-monospace text-primary">User</span>
        <#if entity.userIsDefault()>
            <span class="font-monospace text-secondary">DEFAULT</span>
        <#else>
            ${entity.user}
        </#if>
    </#if>
    <#if !entity.userIsAny() && !entity.clientIsAny()>
        <span class="font-monospace text-secondary">|</span>
    </#if>
    <#if !entity.clientIsAny()>
        <span class="font-monospace text-primary">ClientId</span>
        <#if entity.clientIsDefault()>
            <span class="font-monospace text-secondary">DEFAULT</span>
        <#else>
            ${entity.clientId}
        </#if>
    </#if>
</#macro>

<#macro quotaValue inspection source name suffix>
    <#if (inspection[source])??>
        <#if (inspection[source][name])??>
            <span class="code">
                <#assign value = inspection[source][name]>
                <span class="conf-value" data-name="${name}" data-value="${value?c}">${value}${suffix}</span>
            </span>
        <#else>
            <span class="badge bg-secondary">NONE</span>
        </#if>
    <#else>
        ---
    </#if>
</#macro>
