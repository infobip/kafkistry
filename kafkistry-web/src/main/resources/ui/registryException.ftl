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