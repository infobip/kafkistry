
<#-- @ftlvariable name="historyChangeRequests"  type="java.util.List<com.infobip.kafkistry.service.history.PendingRequest>" -->
<#-- @ftlvariable name="gitBranchBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/util.ftl" as util>

<table class="table m-0">
    <thead class="thead-dark table-sm">
    <tr>
        <th>Type</th>
        <th>User</th>
        <th>Commit</th>
        <th>Message</th>
    </tr>
    </thead>
    <tbody>
    <#if historyChangeRequests?size == 0>
        <tr>
            <td colspan="100">
                <i>(no history changes)</i>
            </td>
        </tr>
    </#if>
    <#list historyChangeRequests as request>
        <#list request.commitChanges as commitChange>
            <#assign commit = commitChange.commit>
            <tr>
                <td>
                    <span class="text-nowrap">
                        <#if commit.merge>
                            <span class="badge badge-secondary">MERGE</span>
                        </#if>
                        <span class="badge ${util.changeTypeClass(request.type)}">
                            ${request.type}
                        </span>
                    </span>
                </td>
                <td>
                    <span>${commit.username}</span>
                </td>
                <td>
                    ${util.commitHashUrl(commit.commitId, gitCommitBaseUrl!'', gitEmbeddedBrowse)}<br/>
                    <small class="time" data-time="${(commit.timestampSec*1000)?c}"></small>
                </td>
                <td>
                    <#if request.errorMsg??>
                        <div role="alert" class="alert alert-danger" title="${request.errorMsg}">
                            Revision with corrupted content!
                        </div>
                    </#if>
                    <div class="p-0">
                        <pre class="text-sm text-secondary m-0" style="max-height:200px; white-space: pre-wrap; overflow-y: scroll;"><small class="text-links git-links">${commit.message}</small></pre>
                    </div>
                </td>
            </tr>
        </#list>
    </#list>
    </tbody>
</table>

