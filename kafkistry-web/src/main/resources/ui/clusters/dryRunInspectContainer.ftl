<div>
    <button id="cluster-dry-run-inspect-btn" class="btn btn-sm btn-outline-secondary">
        Dry run inspect
    </button>

    <#assign statusId = "cluster-dry-run-inspect">
    <#include "../common/serverOpStatus.ftl">
    <#assign statusId = "">

    <div id="cluster-dry-run-inspect-summary" class="m-2"></div>
    <div id="cluster-dry-run-inspect-result"></div>

</div>
<br/>