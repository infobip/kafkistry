<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="_csrf" type="org.springframework.security.web.csrf.CsrfToken" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitBranchRequiredJiraKey"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitMainBranch"  type="java.lang.String" -->
<#-- @ftlvariable name="customJsScripts"  type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="imageProps"  type="com.infobip.kafkistry.webapp.ImageProperties" -->

<#if _csrf??>
    <meta name="_csrf" content="${_csrf.token}"/>
    <meta name="_csrf_header" content="${_csrf.headerName}"/>
</#if>
<#if jiraBaseUrl??>
    <meta name="jira-base-url" content="${jiraBaseUrl}"></meta>
</#if>

<#if gitCommitBaseUrl??>
    <meta name="git-commit-base-url" content="${gitCommitBaseUrl}"></meta>
</#if>
<meta name="git-embedded-browse" content="${gitEmbeddedBrowse?then("yes", "no")}"></meta>
<meta name="git-branch-required-jira-key" content="${gitBranchRequiredJiraKey?then("yes", "no")}"></meta>
<meta name="git-main-branch" content="${gitMainBranch}"></meta>

<#assign rootPath = appUrl.basePath()>

<meta charset="utf-8">
<base href="${rootPath}/">

<#-- Apply theme immediately to prevent flash of wrong theme -->
<script>
(function() {
    const theme = localStorage.getItem('kafkistry-theme') || 'auto';
    const resolvedTheme = theme === 'auto'
        ? (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
        : theme;
    document.documentElement.setAttribute('data-bs-theme', resolvedTheme);
})();
</script>

<script src="https://code.jquery.com/jquery-3.3.1.js"></script>
<script src="https://code.jquery.com/ui/1.12.1/jquery-ui.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/yamljs/0.3.0/yaml.js"></script>
<link rel="stylesheet" href="https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css">

<#if customJsScripts??>
    <#list customJsScripts as customJsScript>
        <script src="${customJsScript}?ver=${lastCommit}"></script>
    </#list>
</#if>

<script src="static/themeManager.js?ver=${lastCommit}"></script>
<script src="static/selectPicker.js?ver=${lastCommit}"></script>
<script src="static/urlManager.js?ver=${lastCommit}"></script>
<script src="static/globalErrorHandler.js?ver=${lastCommit}"></script>
<script src="static/ajaxCsrfRegister.js?ver=${lastCommit}"></script>
<script src="static/xhrLogger.js?ver=${lastCommit}"></script>
<script src="static/dateTimeFormatter.js?ver=${lastCommit}"></script>
<script src="static/util.js?ver=${lastCommit}"></script>
<script src="static/menu.js?ver=${lastCommit}"></script>
<script src="static/cancelGoBack.js?ver=${lastCommit}"></script>
<script src="static/documentationInfoTooltip.js?ver=${lastCommit}"></script>
<script src="static/serverOpStatus.js?ver=${lastCommit}"></script>
<script src="static/valuePrettifier.js?ver=${lastCommit}"></script>
<script src="static/initAutocomplete.js?ver=${lastCommit}"></script>
<script src="static/selectLocation.js?ver=${lastCommit}"></script>
<script src="static/collapsing.js?ver=${lastCommit}"></script>
<script src="static/textLinks.js?ver=${lastCommit}"></script>
<#if gitStorageEnabled>
    <script src="static/git/branchNamesAutocomplete.js?ver=${lastCommit}"></script>
</#if>
<script src="static/datatable.js?ver=${lastCommit}"></script> <!-- init datatable last to allow decoration script (textLinks) to exec first -->
<script src="static/search/globalSearch.js?ver=${lastCommit}"></script>

<link rel="stylesheet" href="static/css/main.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/infoIconTooltip.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/autocompleteDropdown.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/textDiff.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/cross.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/datatable.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/record.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/topicForm.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/collapsing.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/stickyTableHeaders.css?ver=${lastCommit}">
<link rel="stylesheet" href="static/css/search.css?ver=${lastCommit}">

<link rel="icon" href="static/img/${imageProps.dirPath}/${imageProps.icon}?ver=${lastCommit}"/>

<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH" crossorigin="anonymous">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz" crossorigin="anonymous"></script>

<script src="https://cdnjs.cloudflare.com/ajax/libs/diff_match_patch/20121119/diff_match_patch_uncompressed.js"></script>

<script src="https://cdn.datatables.net/1.13.7/js/jquery.dataTables.min.js"></script>
<script src="https://cdn.datatables.net/1.13.7/js/dataTables.bootstrap5.min.js"></script>
<link rel="stylesheet" href="https://cdn.datatables.net/1.13.7/css/dataTables.bootstrap5.min.css">
<script src="https://cdn.jsdelivr.net/g/mark.js(jquery.mark.min.js)"></script>
<script src="https://cdn.datatables.net/plug-ins/1.10.13/features/mark.js/datatables.mark.js"></script>

<script src="https://cdn.jsdelivr.net/gh/caldwell/renderjson@1.4.0/renderjson.js"></script>

<script src="https://cdn.jsdelivr.net/g/filesaver.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.24.0/moment.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery-datetimepicker/2.5.20/jquery.datetimepicker.full.min.js"></script>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/jquery-datetimepicker/2.5.20/jquery.datetimepicker.min.css">

<script src="https://cdn.jsdelivr.net/npm/json-parse-bigint@1.0.2/index.min.js"></script>

<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/css/bootstrap-select.min.css">
<script src="https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta3/dist/js/bootstrap-select.min.js"></script>

<script src="static/diffHtml.js?ver=${lastCommit}"></script>
<script src="static/yamlDiff.js?ver=${lastCommit}"></script>
<script src="static/tickingStatus.js?ver=${lastCommit}"></script>
