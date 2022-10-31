<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="backgroundJobStatuses"  type="java.util.List<com.infobip.kafkistry.service.background.BackgroundJobStatus>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: About</title>
    <meta name="current-nav" content="nav-app-info"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<div class="container">

    <#assign activeNavItem = "background-jobs">
    <#include "submenu.ftl">

    <table class="table table-bordered datatable">
        <thead class="thead-dark">
        <tr>
            <th>Job</th>
            <th></th>
            <th>Cluster</th>
            <th>Last</th>
            <th>Status</th>
        </tr>
        </thead>
        <#list backgroundJobStatuses as jobStatus>
            <tr>
                <td>
                    <pre class="pre-message"><#t>
                        ${jobStatus.job.key.category}<#if jobStatus.job.key.phase??>: ${jobStatus.job.key.phase}</#if><#t>
                    </pre>
                </td>
                <td>
                    <#assign tooltip>
                        <strong>Executing class</strong>: <code>${jobStatus.job.key.jobClass}</code>
                        <p>${jobStatus.job.description}</p>
                    </#assign>
                    <@info.icon tooltip=tooltip/>
                </td>
                <td>
                    <#if jobStatus.job.key.cluster??>
                        <a href="${appUrl.clusters().showCluster(jobStatus.job.key.cluster)}">
                            ${jobStatus.job.key.cluster}
                        </a>
                    <#else>
                        ---
                    </#if>
                </td>
                <td class="time small" data-time="${jobStatus.timestamp?c}"></td>
                <td>
                    <#if jobStatus.lastSuccess>
                        <span class="badge badge-success">SUCCESS</span>
                    <#else>
                        <span class="badge badge-danger">FAILED</span>
                    </#if>
                    <#if jobStatus.lastFailureMessage??>
                        <div class="alert alert-danger mt-1">
                            <pre class="pre-message">${jobStatus.lastFailureMessage}</pre>
                        </div>
                    </#if>
                </td>
            </tr>
        </#list>
    </table>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>