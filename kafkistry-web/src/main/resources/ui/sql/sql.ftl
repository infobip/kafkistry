<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="query" type="java.lang.String" -->
<#-- @ftlvariable name="tables" type="java.util.List<com.infobip.kafkistry.sql.TableInfo>" -->
<#-- @ftlvariable name="tableStats" type="java.util.List<com.infobip.kafkistry.sql.TableStats>" -->
<#-- @ftlvariable name="queryExamples" type="java.util.List<com.infobip.kafkistry.sql.QueryExample>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/sql-js/sql.js?ver=${lastCommit}"></script>
    <script src="static/sql-js/chart.js?ver=${lastCommit}"></script>
    <link rel="stylesheet" href="static/css/sql.css?ver=${lastCommit}"/>
    <#include "../codeMirrorResources.ftl">
    <script src='https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.9.3/Chart.min.js'></script>
    <script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-colorschemes"></script>
    <title>Kafkistry: SQL</title>
    <meta name="current-nav" content="nav-sql"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#assign tableCounts = {}>
<#list tableStats as ts>
    <#assign tableCounts = tableCounts + {ts.name: ts.count}>
</#list>

<div id="table-columns" style="display: none;">
    <#list tables as table>
        <div class="sql-table" data-table="${table.name}">
            <#list table.columns as column>
                <div class="sql-column" data-column="${column.name}"></div>
            </#list>
        </div>
    </#list>
</div>

<div class="container">
    <div style="border: 1px solid var(--bs-border-color);">
        <textarea id="sql-edit" class="width-full" name="sql"
                  placeholder="enter your 'SELECT' SQL query... (CTRL+SPACE or CONTROL+SPACE to autocomplete tables/columns)"
        >${query!''}</textarea>
    </div>
    <div class="pt-2">
        <button id="execute-sql-btn" class="btn btn-primary">Execute SQL</button>
        <label class="small">
            <i>Auto add <code>LIMIT</code> clause if not present</i>
            <input type="checkbox" name="auto-limit" checked>
        </label>
        <div class="float-end">
            <button id="schema-help-btn" type="button" class="btn btn-outline-info">
                Schema help
            </button>
            <button type="button" class="btn btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown"
                    aria-haspopup="true"
                    aria-expanded="false">
                Query examples...
            </button>
            <div class="dropdown-menu">
                <#list queryExamples as query>
                    <span class="dropdown-item query-example" data-sql="${query.sql}">${query.title}</span>
                </#list>
            </div>
        </div>
    </div>
    <div id="schema-info" style="display: none;">
        <table class="table table-hover table-sm mt-2">
            <thead class="thead-light h5">
            <tr>
                <th><span>#</span></th>
                <th><span>Table name</span></th>
                <th><span>Column names</span></th>
            </tr>
            </thead>
            <#list tables as table>
                <tr class="no-hover">
                    <td class="text-end">
                        <#if tableCounts[table.name]??>
                            <#assign allCount = tableCounts[table.name]>
                            <small title="Number of rows in table: ${allCount?c}">${_util.prettyNumber(allCount)}</small>
                        <#else>
                            <small title="Unknown number of rows in table">n/a</small>
                        </#if>
                    </td>
                    <#if !table.joinTable>
                        <#assign tableTdClass = "">
                        <#assign tableNameClass = "alert-primary">
                    <#else>
                        <#assign tableTdClass = "pl-4">
                        <#assign tableNameClass = "alert-info">
                    </#if>
                    <td class="${tableTdClass}">
                        <span class="sql-table sql-insert-text alert alert-inline alert-sm ${tableNameClass} p-1 mb-0">${table.name}</span>
                    </td>
                    <td>
                        <#list table.columns as column>
                            <#if column.joinKey>
                                <#assign colClass = "bg-black font-weight-bold">
                            <#elseif column.primaryKey>
                                <#assign colClass = "bg-primary font-weight-bold">
                            <#elseif column.referenceKey>
                                <#assign colClass = "bg-success font-weight-bold">
                            <#else>
                                <#assign colClass = "bg-secondary">
                            </#if>
                            <#assign tooltip>
                                <#if column.joinKey>
                                    <span class='badge ${colClass}'>FOREIGN KEY</span>
                                <#elseif column.primaryKey>
                                    <span class='badge ${colClass}'>PRIMARY KEY</span>
                                <#elseif column.referenceKey>
                                    <span class='badge ${colClass}'>JOIN KEY</span>
                                <#else>
                                    <span class='badge ${colClass}'>VALUE</span>
                                </#if>
                                <br/>
                                <span class='mt-1 badge bg-danger'>${column.type}</span>
                            </#assign>
                            <span class="sql-column sql-insert-text info-label badge ${colClass}" title="${tooltip}" data-bs-toggle="tooltip"
                                  data-bs-html="true">
                                ${column.name}
                            </span>
                        </#list>
                    </td>
                </tr>
            </#list>

        </table>
    </div>
    <hr/>

    <#include "../common/serverOpStatus.ftl">

    <div id="chart-components-wrapper" class="width-full mb-2" style="display: none;">
        <div class="text-end">
            <button class="btn btn-sm btn-outline-secondary" data-bs-target="#chart-components" data-bs-toggle="collapse">
                <span class="if-collapsed">▼ Show chart</span>
                <span class="if-not-collapsed">△ Hide chart</span>
            </button>
        </div>
        <div id="chart-components" class="showing collapse show">
            <div id="chart-container" class="m-4 bg-body-tertiary" style="display: none;">
                <canvas id="chart" width="1000" height="350" style="margin: auto;"></canvas>
            </div>
            <#assign statusId = "chart">
            <#include "../common/serverOpStatus.ftl">
        </div>
    </div>

    <div id="sql-result"></div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>