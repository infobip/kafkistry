<div class="mt-2 mb-2">
    <div class="text-end">
        <button id="refresh-git-now-btn" class="btn btn-secondary">
            Refresh git now <@_info.icon tooltip=_doc.refreshGitBtn/>
        </button>
    </div>

    <#assign statusId = "git">
    <#include "../common/serverOpStatus.ftl">
    <#assign statusId = "">
</div>
