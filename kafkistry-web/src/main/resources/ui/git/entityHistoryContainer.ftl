<#-- @ftlvariable name="historyUrl" type="java.lang.String" -->

<div class="card">
    <div class="card-header">
        <h4>Changes history</h4>
    </div>
    <div class="card-body p-0 history-container" data-url="${historyUrl}">
        <#assign statusId = "historyChanges">
        <#include "../common/serverOpStatus.ftl">
        <#assign statusId = "">
        <div class="history-table"></div>
    </div>
</div>
