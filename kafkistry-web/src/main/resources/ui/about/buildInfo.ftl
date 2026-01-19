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
                <table class="table table-hover m-0">
                    <tr>
                        <th>Group ID</th><td>${module.maven.groupId}</td>
                    </tr>
                    <tr>
                        <th>Artifact ID</th><td>${module.maven.artifactId}</td>
                    </tr>
                    <tr>
                        <th>Version</th><td>${module.git.build.version}</td>
                    </tr>
                    <#if !module.git.closest.tag.name?contains(module.git.build.version)>
                        <tr>
                            <th>Closest release tag</th>
                            <td>
                                ${module.git.closest.tag.name}
                                <#if module.git.closest.tag.commit.count gt 0>
                                    / ${module.git.closest.tag.commit.count} commit(s) since
                                </#if>
                            </td>
                        </tr>
                    </#if>
                    <tr>
                        <th>Build time</th><td class="time" data-time="${module.git.build.time?long?c}">---</td>
                    </tr>
                    <tr>
                        <th>Commit Time</th><td class="time" data-time="${module.git.commit.time?long?c}">---</td>
                    </tr>
                    <tr>
                        <th>Commit ID</th>
                        <#assign commitBrowseUrl = module.git.remote.browse.commitPrefixUrl + module.git.commit.id.full>
                        <td>
                            <a href="${commitBrowseUrl}" target="_blank"><code>${module.git.commit.id.full}</code></a>
                        </td>
                    </tr>
                    <tr>
                        <th>Commit message</th>
                        <td><pre style="white-space: pre-wrap;">${module.git.commit.message.full}</pre></td>
                    </tr>
                    <tr>
                        <th>Remote URL</th>
                        <td>
                            Browse: <a href="${module.git.remote.browse.url}" target="_blank">${module.git.remote.browse.url}</a>
                            <br/>
                            Origin: <a href="${module.git.remote.origin.url}" target="_blank">${module.git.remote.origin.url}</a>
                        </td>
                    </tr>
                </table>
            </div>
        </div>
    </#list>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>