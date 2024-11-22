<#-- @ftlvariable name="historyUrl" type="java.lang.String" -->

<div class="card">
    <div class="card-header">
        <h4>Changes history</h4>
    </div>
    <div class="card-body p-0 history-container" data-url="${historyUrl}">
        <div class="history-table"></div>
        <#assign statusId = "historyChanges">
        <#include "../common/serverOpStatus.ftl">
        <#assign statusId = "">
    </div>
</div>
