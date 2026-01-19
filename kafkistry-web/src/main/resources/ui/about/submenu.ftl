<#-- @ftlvariable name="activeNavItem" type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="autopilotEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<style>
    #sub-navbar .nav-item .nav-link {
        padding-bottom: 1em;
    }
</style>

<nav id="sub-navbar" class="navbar navbar-expand-sm navbar-dark bg-dark nav-tabs mb-2 pb-0"
    style="padding-bottom: 1em;">
    <#assign activeClasses = "active bg-white text-secondary">
    <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav mr-auto">
            <li class="nav-item ">
                <a class="nav-link <#if activeNavItem == "build-info">${activeClasses}</#if>"
                   href="${appUrl.about().showBuildInfo()}">Build info<span class="visually-hidden">(current)</span></a>
            </li>
            <li class="nav-item ">
                <a class="nav-link <#if activeNavItem == "users-sessions">${activeClasses}</#if>"
                   href="${appUrl.about().showUsersSessions()}">Users sessions<span class="visually-hidden">(current)</span></a>
            </li>
            <li class="nav-item ">
                <a class="nav-link <#if activeNavItem == "scraping-statuses">${activeClasses}</#if>"
                   href="${appUrl.about().showScrapingStatuses()}">Scraping statuses<span class="visually-hidden">(current)</span></a>
            </li>
            <li class="nav-item ">
                <a class="nav-link <#if activeNavItem == "background-jobs">${activeClasses}</#if>"
                   href="${appUrl.about().showBackgroundJobs()}">Background jobs<span class="visually-hidden">(current)</span></a>
            </li>
            <li class="nav-item ">
                <a class="nav-link <#if activeNavItem == "environment">${activeClasses}</#if>"
                   href="${appUrl.about().showEnvironment()}">Environment<span class="visually-hidden">(current)</span></a>
            </li>
            <#if autopilotEnabled>
                <li class="nav-item ">
                    <a class="nav-link <#if activeNavItem == "autopilot">${activeClasses}</#if>"
                       href="${appUrl.autopilot().showAutopilotPage()}">Autopilot<span class="visually-hidden">(current)</span></a>
                </li>
            </#if>
            <#if gitStorageEnabled>
                <li class="nav-item ">
                    <a class="nav-link <#if activeNavItem == "git">${activeClasses}</#if>"
                       href="${appUrl.history().showRecent()}">Git<span class="visually-hidden">(current)</span></a>
                </li>
            </#if>
        </ul>
    </div>
</nav>