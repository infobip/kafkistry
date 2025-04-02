<#-- @ftlvariable name="exceptionMessage" type="java.lang.String" -->
<#-- @ftlvariable name="attrsDump" type="java.util.Map<java.lang.String, java.lang.String>" -->

<#if !(exceptionMessage??)>
    <#assign exceptionMessage = "---">
</#if>
<div class="alert alert-danger">
    <span class="message font-weight-bold">Error message</span>:
    <pre class="pre-message" style="color: #e80000;">${exceptionMessage}</pre>
    <#if attrsDump?? && attrsDump?size gt 0>
        <br/>
        <h6>Request attributes dump:</h6>
        <ul>
            <#list attrsDump as attrName, attrVal>
                <li><code>${attrName}</code> - <#if attrVal??>${attrVal}<#else><code>null</code></#if></li>
            </#list>
        </ul>
    </#if>
</div>