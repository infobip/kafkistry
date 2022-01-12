<#-- @ftlvariable name="exceptionMessage" type="java.lang.String" -->

<#if !(exceptionMessage??)>
    <#assign exceptionMessage = "---">
</#if>
<div class="alert alert-danger">
    <span class="message font-weight-bold">Error message</span>:
    <pre class="pre-message" style="color: #e80000;">${exceptionMessage}</pre>
</div>