<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="_csrf" type="org.springframework.security.web.csrf.CsrfToken" -->
<#-- @ftlvariable name="SPRING_SECURITY_LAST_EXCEPTION" type="java.lang.Exception" -->

<html lang="en">

<head>
    <#include "commonResources.ftl"/>
    <title>Kafkistry: Login</title>
    <link rel="stylesheet" href="static/css/login.css?ver=${lastCommit}">
</head>

<body>

<div class="container login-container">

    <main class="login-form">

        <div class="card">
            <div class="card-header">
                <span class="float-left"><b>Kafkistry</b></span>

                <#if RequestParameters.logout??>
                    <span style="color: green" class="float-right">You have been logged out</span>
                <#elseif RequestParameters.error??>
                    <span style="color: red" class="float-right">
                        Login failed<#if SPRING_SECURITY_LAST_EXCEPTION??>, reason: ${SPRING_SECURITY_LAST_EXCEPTION.message}</#if>
                    </span>
                </#if>
            </div>

            <div class="card-body">
                <form action="login" method="POST">
                    <div class="form-group row">
                        <label for="username" class="col-md-4 col-form-label text-md-right">Username</label>
                        <div class="col-md-6">
                            <input type="text" id="email_address" class="form-control" name="username" required
                                   autofocus>
                        </div>
                    </div>

                    <div class="form-group row">
                        <label for="password" class="col-md-4 col-form-label text-md-right">Password</label>
                        <div class="col-md-6">
                            <input type="password" id="password" class="form-control" name="password" required>
                        </div>
                    </div>

                    <div class="col-md-6 offset-md-4">
                        <button type="submit" class="btn btn-primary">
                            Login
                        </button>
                    </div>

                    <#if _csrf??>
                        <input class="btn btn-primary" name="${_csrf.parameterName}" type="hidden" value="${_csrf.token}"/>
                    </#if>
                </form>
            </div>
        </div>
    </main>
</div>

</body>
</html>
