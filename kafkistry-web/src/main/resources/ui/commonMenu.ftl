
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="kafkistryUser"  type="com.infobip.kafkistry.webapp.security.User" -->
<#-- @ftlvariable name="_csrf" type="org.springframework.security.web.csrf.CsrfToken" -->
<#-- @ftlvariable name="autopilotEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="securityEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="menuItems"  type="java.util.List<com.infobip.kafkistry.webapp.menu.MenuItem>" -->
<#-- @ftlvariable name="backgroundJobIssueGroups"  type="java.util.List<com.infobip.kafkistry.service.background.BackgroundJobIssuesGroup>" -->
<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="imageProps"  type="com.infobip.kafkistry.webapp.ImageProperties" -->
<#-- @ftlvariable name="customUserDetailsTemplate"  type="java.lang.String" -->


<#import "common/documentation.ftl" as _doc>
<#import "common/infoIcon.ftl" as _info>
<#import "common/util.ftl" as _util>

<nav class="navbar navbar-expand-sm bg-body-tertiary nav-tabs p-1 pb-0 w-100" style="position: sticky; top: 0; z-index: 1030;">

  <div class="collapse navbar-collapse" id="kafkistry-navbar">
    <ul class="navbar-nav me-auto">
      <li class="nav-item">
        <a id="nav-home" class="nav-link" href="${appUrl.main().url()}">
            &nbsp;
            <img src="static/img/${imageProps.dirPath}/${imageProps.logo}?ver=${lastCommit}"
                 style="margin: -5px; height: 1.9em;"
                 alt="Home" title="Home"/>
            &nbsp;
            <span class="visually-hidden">(current)</span>
        </a>
      </li>
      <li class="nav-item">
          <a id="nav-clusters" class="nav-link" href="${appUrl.clusters().showClusters()}">Clusters<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-topics" class="nav-link" href="${appUrl.topics().showTopics()}">Topics<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-acls" class="nav-link" href="${appUrl.acls().showAll()}">Acls<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-quotas" class="nav-link" href="${appUrl.quotas().showAll()}">Quotas<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-consumer-groups" class="nav-link" href="${appUrl.consumerGroups().showAllClustersConsumerGroups()}">Consumers<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-consume" class="nav-link" href="${appUrl.consumeRecords().showConsumePage()}">Consume<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-produce" class="nav-link" href="${appUrl.produceRecords().showProducePage()}">Produce<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-kstream" class="nav-link" href="${appUrl.kStream().showAll()}">KStream<span class="visually-hidden">(current)</span></a>
      </li>
      <li class="nav-item">
        <a id="nav-sql" class="nav-link" href="${appUrl.sql().showSqlPage()}">SQL<span class="visually-hidden">(current)</span></a>
      </li>
      <#list menuItems as menuItem>
        <li class="nav-item">
          <a id="${menuItem.id}" class="nav-link" href="${appUrl.menuItem(menuItem)}">${menuItem.name} <#if menuItem.newItem><@_util.newTag/></#if><span class="visually-hidden">(current)</span></a>
        </li>
      </#list>
      <li class="nav-item">
          <a id="nav-app-info" class="nav-link" href="${appUrl.about().showAboutPage()}">About<span class="visually-hidden">(current)</span></a>
      </li>

    </ul>

    <#if kafkistryUser??>
      <div>
        <#assign userTooltip>
            <table class='table mb-0'>
              <tr>
                <td><strong>Username</strong>: ${kafkistryUser.username}</td>
              </tr>
              <tr>
                <td><strong>Email</strong>: ${kafkistryUser.email}</td>
              </tr>
              <tr>
                <td><strong>Role</strong>: <code>${kafkistryUser.role.authority}</code></td>
              </tr>
              <tr>
                <td><strong>Authorities</strong>:
                  <br/>
                  <#list kafkistryUser.authorities as authority>
                    <code>${authority.authority}</code> <#if !authority?is_last><br/></#if>
                  </#list>
                </td>
              </tr>
              <#if customUserDetailsTemplate??>
                <#assign userDetailsTemplate = '<#include "/${customUserDetailsTemplate}.ftl">'?interpret>
                <tr>
                    <td>
                        <@userDetailsTemplate/>
                    </td>
                </tr>
              </#if>
            </table>
        </#assign>
        <strong>
            <@_info.icon tooltip=userTooltip text=kafkistryUser.fullName/>
        </strong>
      </div>
    </#if>

    <#-- Theme Picker Dropdown -->
    <div class="dropdown mx-1" id="theme-selector">
      <a class="nav-link dropdown-toggle" type="button" id="theme-dropdown" data-bs-toggle="dropdown" aria-expanded="false" title="Change theme">
        <span id="theme-icon">🔄</span>
      </a>
      <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="theme-dropdown">
        <li>
          <a class="dropdown-item" href="#" data-theme-selector data-theme="light">
            ☀️ Light
          </a>
        </li>
        <li>
          <a class="dropdown-item" href="#" data-theme-selector data-theme="dark">
            🌙 Dark
          </a>
        </li>
        <li>
          <a class="dropdown-item" href="#" data-theme-selector data-theme="auto">
            🔄 Auto
          </a>
        </li>
      </ul>
    </div>

    <#if securityEnabled>
      <div class="ms-1">
        <form method="post" action="logout" style="display: inline-block; margin: 0">
          <#if _csrf??>
            <input name="${_csrf.parameterName}" type="hidden" value="${_csrf.token}"/>
          </#if>
          <button title="Logout" class="btn btn-danger">
              ⏻
          </button>
        </form>
      </div>
    </#if>

  </div>
</nav>

<script>
    function showMoreIssues() {
        $('#more-issues-btn').hide();
        $('.issue').show();
    }
</script>

<div class="container">
    <#assign maxShown = 6>
    <#assign issuesCount = 0>
    <#list backgroundJobIssueGroups as issuesGroup>
        <#assign issuesCount += issuesGroup.issues?size>
    </#list>
    <#assign elemIndex = 0>
    <#assign shownCount = 0>
    <#list backgroundJobIssueGroups as issuesGroup>
        <#if issuesGroup.issues?size gt 1>
            <div class="alert alert-danger issue collapsed" role="alert"
                 data-bs-target=".issue-group-${issuesGroup?index}" data-toggle="collapsing"
                 <#if elemIndex gte maxShown>style="display: none;" </#if>>
                <span class="when-collapsed" title="expand...">▼</span>
                <span class="when-not-collapsed" title="collapse...">△</span>
                <span class="message">Background jobs failed for ${issuesGroup.groupKey} (${issuesGroup.issues?size} issues)</span>
                <div class="issue-group-${issuesGroup?index} collapseable">
                    <ul>
                        <#list issuesGroup.issues as issue>
                            <li class="m-2">
                                <span class="message">${issue.job.description}</span>
                                <pre class="pre-message">${issue.failureMessage}</pre>
                            </li>
                        </#list>
                    </ul>
                </div>
            </div>
            <#if elemIndex lt maxShown>
                <#assign shownCount += issuesGroup.issues?size>
            </#if>
            <#assign elemIndex++>
        <#else>
            <#list issuesGroup.issues as issue>
                <div class="alert alert-danger issue collapsed" role="alert"
                     data-bs-target=".issue-msg-${elemIndex}" data-toggle="collapsing"
                     <#if elemIndex gte maxShown>style="display: none;" </#if>>
                    <span class="when-collapsed" title="expand...">▼</span>
                    <span class="when-not-collapsed" title="collapse...">△</span>
                    <span class="message">Background job failed: ${issue.job.description}</span>
                    <pre class="pre-message issue-msg-${elemIndex} collapseable">${issue.failureMessage}</pre>
                </div>
                <#if elemIndex lt maxShown>
                    <#assign shownCount++>
                </#if>
                <#assign elemIndex++>
            </#list>
        </#if>
    </#list>
    <#assign leftIssues = issuesCount - shownCount>
    <#if leftIssues gt 0>
        <div id="more-issues-btn">
            <button class="btn btn-sm btn-outline-danger" onclick="showMoreIssues();">
                And ${leftIssues} more failed background jobs...
            </button>
            <br/>
            <br/>
        </div>
    </#if>

</div>

