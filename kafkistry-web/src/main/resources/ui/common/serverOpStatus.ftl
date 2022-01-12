<#-- @ftlvariable name="statusId" type="java.lang.String" -->
<#-- @ftlvariable name="noAutoBack" type="java.lang.Boolean" -->

<#if !(statusId??) || statusId=="">
    <#assign statusId = 'server-op-status'>
</#if>
<div id="${statusId!'server-op-status'}" class="mt-2">
    <div class="progress-status info" style="display: none">
        <div class="alert alert-secondary">
            <span class="message"></span>
            <pre class="pre-message"></pre>
        </div>
    </div>
    <div class="success-status info" style="display: none">
        <div class="alert alert-success">
            <span class="message"></span>
            <pre class="pre-message"></pre>
        </div>
        <#if !(noAutoBack??) || !noAutoBack>
            <#include "backBtn.ftl">
        </#if>
    </div>
    <div class="error-status info" style="display: none">
        <div class="alert alert-danger">
            <span class="message"></span>
            <pre class="pre-message"></pre>
        </div>
    </div>
</div>