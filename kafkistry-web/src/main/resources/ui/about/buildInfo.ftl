<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="modules"  type="java.util.List<com.infobip.kafkistry.appinfo.ModuleBuildInfo>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: About</title>
    <meta name="current-nav" content="nav-app-info"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>

<div class="container">

    <#assign activeNavItem = "build-info">
    <#include "submenu.ftl">

    <#list modules as module>
        <div class="card">
            <div class="card-header h5">
                Module: ${module.maven.artifactId}
            </div>
            <div class="card-body p-0">
                <table class="table m-0">
                    <tr>
                        <th>Group ID</th><td>${module.maven.groupId}</td>
                    </tr>
                    <tr>
                        <th>Artifact ID</th><td>${module.maven.artifactId}</td>
                    </tr>
                    <tr>
                        <th>Version</th><td>${module.git.build.version}</td>
                    </tr>
                    <tr>
                        <th>Build time</th><td class="time" data-time="${module.git.build.time?long?c}">---</td>
                    </tr>
                    <tr>
                        <th>Commit Time</th><td class="time" data-time="${module.git.commit.time?long?c}">---</td>
                    </tr>
                    <tr>
                        <th>Commit ID</th><td><code>${module.git.commit.id.full}</code></td>
                    </tr>
                </table>
            </div>
        </div>
    </#list>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>