<#macro progressSection total amount colorClass what>
    <#import "../common/util.ftl" as util>
    <#assign percent = 100.0*amount/total>
    <div class="progress-bar-section h-100 d-flex ${colorClass} <#if amount == 0>progress-bar-empty</#if>"
         title="${what} ${amount} record(s) ${util.prettyNumber(percent)}%"
         style="width: ${percent?c}%;">
        <div class="m-auto font-monospace">
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
    </div>
</#macro>

<#macro progressBar total preRetention skip read remain emHeight=1 legend=false>
    <#import "../common/util.ftl" as util>
    <#if total gt 0>
        <#if legend>
            <div class="row m-0 text-center">
                <div class="col-auto">
                    <span class="badge bg-neutral">Begin</span> &rarr;
                </div>
                <div class="col">
                    &rarr; <span class="font-italic">Read progress</span> &rarr;
                </div>
                <div class="col-auto">
                    &rarr; <span class="badge bg-neutral">End</span>
                </div>
            </div>
        </#if>
        <div class="progress-bar w-100 flex-row" style="height: ${emHeight?c}em;">
            <@progressSection total=total amount=preRetention colorClass="deleted" what="Processed retention-deleted"/>
            <@progressSection total=total amount=skip colorClass="skipped" what="Skipped"/>
            <@progressSection total=total amount=read colorClass="processed" what="Processed"/>
            <@progressSection total=total amount=remain colorClass="remaining" what="Remaining"/>
        </div>
    <#else>
        <i>(empty)</i>
    </#if>
</#macro>