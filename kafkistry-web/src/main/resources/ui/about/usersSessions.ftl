<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="usersSessions"  type="java.util.List<com.infobip.kafkistry.webapp.UserSessions>" -->
<#-- @ftlvariable name="requestsStats"  type="java.util.List<com.infobip.kafkistry.webapp.RecordedRequestStats>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: About</title>
    <meta name="current-nav" content="nav-app-info"/>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <#assign activeNavItem = "users-sessions">
    <#include "submenu.ftl">

    <#if usersSessions?size == 0>
        <p><i>(no user sessions)</i></p>
    <#else>
        <p>There are ${usersSessions?size} currently logged-in users</p>
    </#if>

    <div class="card mb-2">
        <div class="card-header collapsed" data-toggle="collapsing" data-target="#global-stats-body">
            <h5>
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
                There are ${requestsStats?size} recorded distinct requests
            </h5>
        </div>
        <div id="global-stats-body" class="card-body p-0 collapseable">
            <table class="table table-sm m-0">
                <thead class="thead-light">
                <tr>
                    <th>Method</th>
                    <th>URI</th>
                    <th>Query</th>
                    <th>Count</th>
                    <th>Usernames</th>
                </tr>
                </thead>
                <tbody>
                <#list requestsStats as requestStats>
                    <tr>
                        <td><span class="badge badge-dark">${requestStats.request.method}</span></td>
                        <td><span class="text-break small">${requestStats.request.uri}</span></td>
                        <td class="text-break">
                            <#if requestStats.request.query??>
                                <span class="small">${requestStats.request.query}</span>
                            <#else>
                                <i>---</i>
                            </#if>
                        </td>
                        <td>${requestStats.metrics.count}</td>
                        <td>${requestStats.metrics.usernames?join(", ")}</td>
                    </tr>
                </#list>
                </tbody>
            </table>
        </div>
    </div>

    <#list usersSessions as userSessions>
        <div class="card mb-2">
            <div class="card-header">
                <div class="form-row">
                    <div class="col">
                        <span class="h5">
                            <#if userSessions.currentUser>
                                <span class="badge badge-primary">YOU</span>
                            </#if>
                            ${userSessions.user.username} - ${userSessions.user.fullName}
                            <#assign roleClass = (userSessions.user.role.name == "ADMIN")?then("badge-danger", "badge-dark")>
                            <span class="badge ${roleClass}">${userSessions.user.role.name}</span>
                        </span>
                    </div>
                    <div class="col-">
                        <span class="">${userSessions.sessions?size} session<#if userSessions.sessions?size != 1>s</#if></span>
                    </div>
                </div>
            </div>
            <div class="card-body p-0">
                <table class="table m-0">
                    <tr class="thead-dark">
                        <th></th>
                        <th>Session ID</th>
                        <th>Expired</th>
                        <th>Last request</th>
                    </tr>
                    <#list userSessions.sessions as session>
                        <tr data-toggle="collapsing" data-target="#recorded-requests-${userSessions?index?c}-${session?index?c}">
                            <td>
                                <span class="when-collapsed" title="expand...">▼</span>
                                <span class="when-not-collapsed" title="collapse...">△</span>
                            </td>
                            <td><code>${session.sessionId}</code></td>
                            <td>
                                <#if session.expired>
                                    <span class="badge badge-danger">YES</span>
                                <#else>
                                    <span class="badge badge-success">NO</span>
                                </#if>
                            </td>
                            <td class="time" data-time="${session.lastRequestTime?c}"></td>
                        </tr>
                        <#if session.recordedRequests??>
                            <tr id="recorded-requests-${userSessions?index?c}-${session?index?c}" class="collapseable">
                                <td colspan="100" class="p-0">
                                    <table class="table table-sm m-0">
                                        <thead class="thead-light">
                                        <tr>
                                            <th>Method</th>
                                            <th>URI</th>
                                            <th>Query</th>
                                            <th>First</th>
                                            <th>Last</th>
                                            <th>Count</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <#list session.recordedRequests.urlRequests as requests>
                                            <tr>
                                                <td><span class="badge badge-dark">${requests.method}</span></td>
                                                <td><span class="text-break small">${requests.uri}</span></td>
                                                <td class="text-break">
                                                    <#if requests.query??>
                                                        <span class="small">${requests.query}</span>
                                                    <#else>
                                                        <i>---</i>
                                                    </#if>
                                                </td>
                                                <td class="time small" data-time="${requests.firstTime?c}"></td>
                                                <td class="time small" data-time="${requests.lastTime?c}"></td>
                                                <td>${requests.count}</td>
                                            </tr>
                                        </#list>
                                        </tbody>
                                    </table>
                                </td>
                            </tr>
                        </#if>
                    </#list>
                </table>
            </div>
        </div>
    </#list>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>