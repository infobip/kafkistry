<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="tagsCounts"  type="java.util.Map<java.lang.String, java.lang.Integer>" -->

<#import "../common/util.ftl" as util>
<#import "../common/violaton.ftl" as violatonUtil>

<#if tagsCounts?size == 0>
    <div class="p-3">
        <a class="btn btn-sm btn-primary" href="${appUrl.clusters().showTags()}#add-new-tag-btn">
            Add tag...
        </a>
    </div>
<#else>
    <div class="p-3">
        <#list tagsCounts as tag, count>
            <a class="m-0 p-0 btn btn-sm btn-outline-light"
               href="${appUrl.clusters().showClusters()}#${tag}" title="Click to filter clusters...">
                <span class="badge badge-secondary">${tag}</span><#t>
                <span class="badge badge-info" title="number of clusters">${count}</span>
            </a>
        </#list>
    </div>
</#if>
