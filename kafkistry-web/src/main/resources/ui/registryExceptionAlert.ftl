<#-- @ftlvariable name="httpStatus" type="org.springframework.http.HttpStatus" -->
<#-- @ftlvariable name="exceptionMessage" type="java.lang.String" -->
<#-- @ftlvariable name="attrsDump" type="java.util.Map<java.lang.String, java.lang.String>" -->

<#if !(exceptionMessage??)>
    <#assign exceptionMessage = "---">
</#if>
<div class="alert alert-danger">

    <#switch httpStatus.series().value()>
        <#case 1>
            <#assign codeClass = "badge-primary">
            <#break>
        <#case 2>
            <#assign codeClass = "badge-success">
            <#break>
        <#case 3>
            <#assign codeClass = "badge-warning">
            <#break>
        <#case 4>
        <#case 5>
            <#assign codeClass = "badge-danger">
            <#break>
        <#default>
            <#assign codeClass = "badge-secondary">
    </#switch>

    <span class="badge ${codeClass}">${httpStatus.getReasonPhrase()} - ${httpStatus.value()}</span>
    <br/>

    <span class="message font-weight-bold">Error message</span>:
    <pre class="pre-message" style="color: #e80000;">${exceptionMessage}</pre>

    <#if attrsDump?? && attrsDump?size gt 0>
        <div class="card card-sm mt-2">
            <div class="card-header p-2 alert-dark" style="cursor: pointer;"
                 data-toggle="collapsing" data-target="#attrsDumpCollapse" aria-expanded="false" aria-controls="attrsDumpCollapse">
                <small class="font-weight-bold">
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    Request attributes dump (${attrsDump?size} attributes)
                </small>
            </div>
            <div class="collapseable alert-secondary" id="attrsDumpCollapse">
                <div class="card-body p-2">
                    <ul class="mb-0" style="font-size: 0.9rem;">
                        <#list attrsDump as attrName, attrVal>
                            <li><code>${attrName}</code> - <#if attrVal??>${attrVal}<#else><code>null</code></#if></li>
                        </#list>
                    </ul>
                </div>
            </div>
        </div>
    </#if>
</div>