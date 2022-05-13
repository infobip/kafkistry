<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="pendingRequests"  type="java.util.List<com.infobip.kafkistry.service.history.PendingRequest>" -->
<#-- @ftlvariable name="gitBranchBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitCommitBaseUrl"  type="java.lang.String" -->
<#-- @ftlvariable name="gitEmbeddedBrowse"  type="java.lang.Boolean" -->

<#import "documentation.ftl" as doc>
<#import "infoIcon.ftl" as info>
<#import "util.ftl" as _util_>

<div class="container">
    <div class="row">
        <#if pendingRequests?? && pendingRequests?size != 0>
            <#list pendingRequests as request>
                <#assign branchName = "refs/heads/" + request.branch>
                <div class="col-sm p-0">
                    <#if request.errorMsg??>
                        <div role="alert" class="alert alert-danger mb-0" title="${request.errorMsg}">
                            Corrupted content!
                        </div>
                    </#if>
                    <span class="badge ${_util_.changeTypeClass(request.type)}">${request.type.name()}</span> in
                    <#if gitBranchBaseUrl??>
                        <#assign url = gitBranchBaseUrl + branchName?url>
                        <a target="_blank" href="${url}">${request.branch}</a>
                    <#elseif gitEmbeddedBrowse>
                        <a href="${appUrl.git().showBranch(request.branch)}">${request.branch}</a>
                    <#else>
                        branch '${request.branch}'
                    </#if>
                    <#if (request.commitChanges?size > 1)>
                        commits:
                    <#else>
                        commit:
                    </#if>
                    <#list request.commitChanges as commitChange>
                        <#assign commit = commitChange.commit>
                        <#assign tooltipHtml>
                            ${commit.username}
                            <br/>
                            ${(commit.timestampSec*1000)?number_to_datetime} UTC
                            <p class='text-light'>${commit.message}</p>
                        </#assign>
                        <span data-username="${commit.username}" data-time-sec="${commit.timestampSec?c}">
                        <@info.icon tooltip=tooltipHtml/>
                        <#if gitCommitBaseUrl??>
                            <#assign commitUrl = gitCommitBaseUrl + commit.commitId>
                            <a <#if !gitEmbeddedBrowse>target="_blank"</#if>
                               href="${commitUrl}">${commit.commitId?substring(0, 6)}</a><#if !commitChange?is_last>, </#if>
                        <#else>
                            ${commit.commitId?substring(0, 6)}<#if !commitChange?is_last>, </#if>
                        </#if>
                        </span>
                    </#list>
                </div>
                <#if request.class.getSimpleName() == "ClusterRequest">
                <#-- @ftlvariable name="clusterRequest"  type="com.infobip.kafkistry.service.history.ClusterRequest" -->
                    <#assign clusterRequest = request>
                    <div class="col- m-1">
                        <a href="${appUrl.clusters().showEditClusterOnBranch(clusterRequest.identifier, clusterRequest.branch)}">
                            <button class="btn btn-outline-primary btn-sm">Edit... <@info.icon tooltip=doc.editPendingChangeBtn/></button>
                        </a>
                    </div>
                </#if>
                <#if request.class.getSimpleName() == "TopicRequest">
                <#-- @ftlvariable name="topicRequest"  type="com.infobip.kafkistry.service.history.TopicRequest" -->
                    <#assign topicRequest = request>
                    <div class="col- m-1">
                        <a href="${appUrl.topics().showEditTopicOnBranch(topicRequest.topicName, topicRequest.branch)}">
                            <button class="btn btn-outline-primary btn-sm">Edit... <@info.icon tooltip=doc.editPendingChangeBtn/></button>
                        </a>
                    </div>
                </#if>
                <#if request.class.getSimpleName() == "AclsRequest">
                <#-- @ftlvariable name="aclsRequest"  type="com.infobip.kafkistry.service.history.AclsRequest" -->
                    <#assign aclsRequest = request>
                    <div class="col- m-1">
                        <a href="${appUrl.acls().showEditPrincipalOnBranch(aclsRequest.principal, aclsRequest.branch)}">
                            <button class="btn btn-outline-primary btn-sm">Edit... <@info.icon tooltip=doc.editPendingChangeBtn/></button>
                        </a>
                    </div>
                </#if>
                <#if request.class.getSimpleName() == "QuotasRequest">
                <#-- @ftlvariable name="quotasRequest"  type="com.infobip.kafkistry.service.quotas.QuotasRequest" -->
                    <#assign quotasRequest = request>
                    <div class="col- m-1">
                        <a href="${appUrl.quotas().showEditEntityOnBranch(quotasRequest.entityID, quotasRequest.branch)}">
                            <button class="btn btn-outline-primary btn-sm">Edit... <@info.icon tooltip=doc.editPendingChangeBtn/></button>
                        </a>
                    </div>
                </#if>
                <#if !request?is_last>
                    <br/>
                </#if>
            </#list>
        <#else>
            <i>-----</i>
        </#if>
    </div>
</div>
