<#-- @ftlvariable name="branch" type="java.lang.String" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitBranchRequiredJiraKey"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="gitMainBranch"  type="java.lang.String" -->

<#import "documentation.ftl" as uf_doc>
<#import "infoIcon.ftl" as uf_info>

<div class="card">
    <div class="card-header h5">Update/commit metadata</div>
    <div class="card-body">
        <label class="col-6">
            Create/update message reason <@uf_info.icon tooltip=uf_doc.updateInputMsg/>:
            <input class="form-control" id="update-message" type="text" placeholder="Why are those changes being made? (jira: ACB-123)">
        </label>

        <#if gitStorageEnabled>
            <label class="col-6">
                Choose branch to write into:
                <#assign branchPlaceholder = gitBranchRequiredJiraKey?then((gitMainBranch) + ' or branch with JIRA key (e.g., ABC-123-feature)', 'custom branch name or (empty) for default')>
                <input class="form-control" name="targetBranch"
                       placeholder="${branchPlaceholder}"
                       value="${branch!''}">
            </label>
        </#if>
    </div>
</div>
