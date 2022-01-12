<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="httpStatus" type="org.springframework.http.HttpStatus" -->
<#-- @ftlvariable name="exceptionMessage" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "commonResources.ftl"/>
    <title>Kafkistry: Request exception</title>
</head>

<body>


<#include "commonMenu.ftl">

<div class="container">
    <div class="row">
        <div class="col-md-12">
            <div class="error-template">
                <h2>Kafkistry error</h2>
                <hr>
                <h4>
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
                    <span class="badge ${codeClass}">${httpStatus.value()?c}</span>
                    ${httpStatus.reasonPhrase}
                </h4>
                <hr>
                <#include "registryExceptionAlert.ftl">
                <div class="error-actions">
                    <#include  "common/backBtn.ftl">
                    <a href="${appUrl.main().url()}" class="btn btn-primary btn-sm">Home</a>
                </div>
            </div>
        </div>
    </div>
</div>

<#include "common/pageBottom.ftl">
</body>
</html>