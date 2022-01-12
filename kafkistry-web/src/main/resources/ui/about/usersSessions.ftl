<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="usersSessions"  type="java.util.List<com.infobip.kafkistry.webapp.UserSessions>" -->

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
                        <th>Session ID</th>
                        <th>Expired</th>
                        <th>Last request</th>
                    </tr>
                    <#list userSessions.sessions as session>
                        <tr>
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
                    </#list>
                </table>
            </div>
        </div>
    </#list>

</div>


<#include "../common/pageBottom.ftl">
</body>
</html>