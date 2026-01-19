<#-- @ftlvariable name="tooltip" type="java.lang.String" -->
<#macro icon tooltip text="?"><#t>
    <span class="info-icon <#if !text?is_markup_output && text?length == 1>circle</#if>"
          title="${tooltip}" data-bs-toggle="tooltip" data-bs-html="true">${text}</span><#t>
</#macro>
