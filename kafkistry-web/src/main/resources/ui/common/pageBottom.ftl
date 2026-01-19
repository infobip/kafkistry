<#-- @ftlvariable name="hostname" type="com.infobip.kafkistry.model.Presence" -->
<#-- @ftlvariable name="requestStartTimeMs" type="java.lang.Long" -->

<#import "util.ftl" as util>

<div style="width: 100%; height: 150px;"></div>
<footer class="footer bg-body-tertiary border-top text-lg-start p-1 w-100" style="position: fixed; bottom: 0; z-index: 1030;">
    <div class="container-fluid" style="position: relative;">
        <div style="position: relative; max-width: 500px;">
            <input
                type="search"
                id="global-search-input"
                class="form-control form-control-sm"
                placeholder="Search topics, clusters, ACLs, quotas..."
                autocomplete="off"
                style="width: 100%; border-radius: 20px; padding-right: 35px;"
            />
            <span style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%); color: #999; pointer-events: none;">
                ⌘K
            </span>
            <div id="global-search-dropdown" class="search-dropdown" style="display: none; bottom: 100%; top: auto; margin-bottom: 5px;"></div>
        </div>
        <div style="position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); white-space: nowrap;">
            Served by: <code>${hostname}</code>
            <#if requestStartTimeMs??>
                <#assign durationMs = .now?long - requestStartTimeMs>
                in <span class="text-muted">${util.prettyDuration(durationMs / 1000)}</span>
            </#if>
        </div>
    </div>
</footer>
