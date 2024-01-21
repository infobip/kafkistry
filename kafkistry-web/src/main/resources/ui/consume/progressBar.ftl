<#macro progressSection total amount colorClass what>
    <#import "../common/util.ftl" as util>
    <#assign percent = 100.0*amount/total>
    <div class="progress-bar-section h-100 ${colorClass} <#if amount == 0>progress-bar-empty</#if>"
         title="${what} ${amount} record(s) ${util.prettyNumber(percent)}%"
         style="width: ${percent?c}%;">
        <#if percent gt 20>
            ${what}: ${util.prettyNumber(percent)}%
        <#elseif percent gt 4>
            ${util.prettyNumber(percent)}%
        <#elseif percent gt 1>
            <svg viewBox="0 0 45 18" class="w-100 h-100">
                <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" style="stroke: white;">
                    ${util.prettyNumber(percent)}%
                </text>
            </svg>
        </#if>
    </div>
</#macro>

<#macro progressBar total preRetention skip read remain>
    <#import "../common/util.ftl" as util>
    <style>
        .progress-bar-section {
            min-width: 0.25em;
            overflow: hidden;
            font-size: 0.75em;
        }
        .progress-bar-empty {
            width: 0 !important;
            min-width: 0 !important;
        }
    </style>
    <#if total gt 0>
        <div class="progress-bar w-100 flex-row" style="height: 1em;">
            <@progressSection total=total amount=preRetention colorClass="bg-danger" what="Processed retention-deleted"/>
            <@progressSection total=total amount=skip colorClass="bg-secondary" what="Skipped"/>
            <@progressSection total=total amount=read colorClass="bg-success" what="Processed"/>
            <@progressSection total=total amount=remain colorClass="bg-primary" what="Remaining"/>
        </div>
    <#else>
        <i>(empty)</i>
    </#if>
</#macro>