<#-- @ftlvariable name="hostname" type="com.infobip.kafkistry.model.Presence" -->
<#-- @ftlvariable name="requestStartTimeMs" type="java.lang.Long" -->

<#import "util.ftl" as util>

<div style="width: 100%; height: 250px;"></div>
<footer class="footer small bg-body-tertiary border-top text-lg-start p-1 w-100" style="position: fixed; bottom: 0; z-index: 1030;">
    <div class="container-fluid text-center small">
        Served by: <code>${hostname}</code>
        <#if requestStartTimeMs??>
            <#assign durationMs = .now?long - requestStartTimeMs>
            in <span class="text-muted">${util.prettyDuration(durationMs / 1000)}</span>
        </#if>
    </div>
</footer>
