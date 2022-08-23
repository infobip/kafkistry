$(document).ready(function () {
    $("#refresh-btn").click(refreshCluster);
    $(document).on("click", ".status-filter-btn", null,function () {
        let statusType = $(this).attr("data-status-type");
        let dataTableId = $(this).attr("data-table-id");
        filterDatatableBy(statusType, dataTableId);
    });
    loadPageSegment("clusterIssues", "issues", "clusters.showClusterIssues", $("#cluster-issues-result"));
    loadPageSegment("clusterTopic", "topics", "clusters.showClusterTopics", $("#cluster-topics-result"));
    loadPageSegment("clusterAcls", "acls", "clusters.showClusterAcls", $("#cluster-acls-result"));
    loadPageSegment("clusterQuotas", "quotas", "clusters.showClusterQuotas", $("#cluster-quotas-result"));
    loadPageSegment("clusterConsumerGroups", "consumer groups", "clusters.showClusterConsumerGroups", $("#cluster-consumer-groups-result"));
    tryMaybeFilterDatatableByUrlHash();
});

function refreshCluster() {
    console.log("Going to refresh cluster state");
    let clusterIdentifier = $("meta[name=cluster-identifier]").attr("content");
    $.post("api/clusters/refresh/cluster", {clusterIdentifier: clusterIdentifier})
        .done(function () {
            console.log("Success on refresh clusters state");
            location.reload();
        })
        .fail(function (error) {
            console.log("Failed refresh clusters state");
            console.log(error);
        });
}

let doneSegments = [];

function loadPageSegment(
    opProgressId, what, urlSchemaTarget, resultContainer
) {
    let clusterIdentifier = $("meta[name=cluster-identifier]").attr("content");
    showOpProgressOnId(opProgressId, "Loading " + what + "...");
    whenUrlSchemaReady(function () {
        let url = urlFor(urlSchemaTarget, {clusterIdentifier: clusterIdentifier});
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnId(opProgressId);
                resultContainer.html(response);
                refreshAllConfValuesIn(resultContainer);
                registerAllInfoTooltipsIn(resultContainer);
                initDatatablesIn(resultContainer);
                let tableId = resultContainer.find(".datatable").attr("id");
                doneSegments.push(tableId);
                tryMaybeFilterDatatableByUrlHash();
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId(opProgressId);
                    resultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId(opProgressId, "Failed to get cluster " + what, errorMsg);
                }
            });
    });
}

function tryMaybeFilterDatatableByUrlHash() {
    let searchOpts = urlHashSearch();
    if (!searchOpts || !searchOpts.tableId) return;
    if (doneSegments.indexOf(searchOpts.tableId) > -1) {
        filterDatatableBy(searchOpts.search, searchOpts.tableId);
    }
}


