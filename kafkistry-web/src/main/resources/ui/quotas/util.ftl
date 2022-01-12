
<#macro entity entity>
<#-- @ftlvariable name="entity" type="com.infobip.kafkistry.model.QuotaEntity" -->
    <#if !entity.userIsAny()>
        <span class="badge badge-dark">User</span>
        <#if entity.userIsDefault()>
            <span class="badge badge-secondary">DEFAULT</span>
        <#else>
            ${entity.user}
        </#if>
    </#if>
    <#if !entity.userIsAny() && !entity.clientIsAny()>|</#if>
    <#if !entity.clientIsAny()>
        <span class="badge badge-dark">ClientId</span>
        <#if entity.clientIsDefault()>
            <span class="badge badge-secondary">DEFAULT</span>
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
            <span class="badge badge-secondary">NONE</span>
        </#if>
    <#else>
        ---
    </#if>
</#macro>
