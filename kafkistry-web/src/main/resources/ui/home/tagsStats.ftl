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
            <a class="m-0 p-0 text-decoration-none text-nowrap"
               href="${appUrl.clusters().showClusters()}#${tag}" title="Click to filter clusters...">
                <span class="badge bg-secondary position-relative me-4">
                    ${tag}
                    <span class="ms-1 badge bg-neutral position-absolute z-1" title="number of clusters">${count}</span>
                </span>
            </a>
        </#list>
    </div>
</#if>
