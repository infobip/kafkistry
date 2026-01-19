<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="allCommits" type="java.util.List<com.infobip.kafkistry.service.history.ChangeCommit<com.infobip.kafkistry.service.history.Change>>" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="jiraBaseUrl"  type="java.lang.String" -->

<#-- @ftlvariable name="datatableId"  type="java.lang.String" -->
<#-- @ftlvariable name="useDatatable"  type="java.lang.Boolean" -->

<#import "../common/util.ftl" as util>

<table class="table table-hover table-bordered m-0 <#if useDatatable>datatable datatable-no-sort</#if>"
        <#if useDatatable && (datatableId??)>id="${datatableId}"</#if>
       <#if useDatatable>style="display: none;"</#if>>
    <thead class="table-theme-dark">
    <tr>
        <th>Type</th>
        <th>Entity</th>
        <th>Name</th>
        <th>User</th>
        <th>Commit</th>
        <th>Message</th>
    </tr>
    </thead>
    <tbody>
    <#assign changesCount = 0>
    <#list allCommits as changeCommit>
        <#list changeCommit.changes as change>
            <#assign changesCount++>
            <tr>
                <td>
                    <span class="text-nowrap">
                        <#if changeCommit.commit.merge>
                            <span class="badge bg-secondary">MERGE</span>
                        </#if>
                        <span class="badge ${util.changeTypeClass(change.changeType)}">
                            ${change.changeType}
                        </span>
                    </span>
                </td>

                <#-- @ftlvariable name="topicChange" type="com.infobip.kafkistry.service.history.TopicChange" -->
                <#-- @ftlvariable name="clusterChange" type="com.infobip.kafkistry.service.history.ClusterChange" -->
                <#-- @ftlvariable name="aclsChange" type="com.infobip.kafkistry.service.history.AclsChange" -->
                <#-- @ftlvariable name="quotasChange" type="com.infobip.kafkistry.service.quotas.QuotasChange" -->
                <#switch change.class.getSimpleName()>
                    <#case "TopicChange">
                        <#assign topicChange = change>
                        <td><span class="badge bg-neutral">TOPIC</span></td>
                        <td>
                            <a href="${appUrl.topics().showTopic(topicChange.topicName)}">${topicChange.topicName}</a>
                        </td>
                        <#break>
                    <#case "ClusterChange">
                        <#assign clusterChange = change>
                        <td><span class="badge bg-neutral">CLUSTER</span></td>
                        <td>
                            <a href="${appUrl.clusters().showCluster(clusterChange.identifier)}">${clusterChange.identifier}</a>
                        </td>
                        <#break>
                    <#case "AclsChange">
                        <#assign aclsChange = change>
                        <td><span class="badge bg-neutral">ACLs</span></td>
                        <td>
                            <a href="${appUrl.acls().showAllPrincipalAcls(aclsChange.principal)}">${aclsChange.principal}</a>
                        </td>
                        <#break>
                    <#case "QuotasChange">
                        <#assign quotasChange = change>
                        <td><span class="badge bg-neutral">QUOTA</span></td>
                        <td>
                            <a href="${appUrl.quotas().showEntity(quotasChange.entityID)}">${quotasChange.entityID}</a>
                        </td>
                        <#break>
                    <#default>
                        <td></td>
                        <td></td>
                        <#break>
                </#switch>
                <td>${changeCommit.commit.username}</td>
                <td>
                    ${util.commitHashUrl(changeCommit.commit.commitId, gitCommitBaseUrl!'', gitEmbeddedBrowse)}
                    <small class="time" data-time="${(changeCommit.commit.timestampSec*1000)?c}"></small>
                </td>
                <td>
                    <pre class="pre-message"><small class="text-links git-links">${changeCommit.commit.message}</small></pre>
                </td>
            </tr>
        </#list>
    </#list>
    <#if !useDatatable && changesCount == 0>
        <tr>
            <td colspan="100" class="text-center">
                <i>(no commits)</i>
            </td>
        </tr>
    </#if>
    </tbody>
</table>
