<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="propertyToEnable" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="featureName" type="java.lang.String" -->

<html lang="en">

<head>
    <#include "commonResources.ftl"/>
    <title>Kafkistry: Feature disabled</title>
</head>

<body>


<#include "commonMenu.ftl">

<div class="container">
    <div class="row">
        <div class="col-md-12">
            <div class="error-template">
                <h2>Kafkistry: feature disabled</h2>
                <hr>

                <div class="card">
                    <div class="card-header">
                        <h4>${featureName} <span class="badge badge-danger">DISABLED</span></h4>
                    </div>
                    <div class="card-body">
                        <p>Enable feature with following application property</p>
                        <div class="alert alert-secondary mb-0">
                            <#list propertyToEnable as propertyName>
                                <code>${propertyName}</code> = <code>true</code>
                                <#if !propertyName?is_last><br/><i class="small">(or with)</i><br/></#if>
                            </#list>
                        </div>
                    </div>
                </div>
                <br/>
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