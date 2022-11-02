$(document).ready(function () {
    whenUrlSchemaReady(function () {
        fetchStats(
            "main.showPendingRequests", "pending-requests", "#pending-requests-container"
        )
        fetchStats(
            "main.showClustersStats", "clusters-stats", "#clusters-stats-container"
        )
        fetchStats(
            "main.showTagsStats", "tags-stats", "#tags-stats-container"
        )
        fetchStats(
            "main.showTopicsStats", "topics-stats", "#topics-stats-container"
        )
        fetchStats(
            "main.showConsumerGroupsStats", "consumer-groups-stats", "#consumer-groups-stats-container"
        )
        fetchStats(
            "main.showAclsStats", "acls-stats", "#acls-stats-container"
        )
        fetchStats(
            "main.showQuotasStats", "quotas-stats", "#quotas-stats-container"
        )
    });
});

function fetchStats(urlTarget, statusId, containerSelector) {
    let container = $(containerSelector);
    if (container.length === 0) return;
    showOpProgressOnId(statusId, "Loading...");
    $
        .ajax(urlFor(urlTarget), {
            method: "GET",
            headers: {ajax: 'true'},
        })
        .done(function (response) {
            hideServerOpOnId(statusId);
            container.html(response);
            registerAllInfoTooltips();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId(statusId);
                container.html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId(statusId, "Failed to fetch stats", errorMsg);
            }
        });
}
