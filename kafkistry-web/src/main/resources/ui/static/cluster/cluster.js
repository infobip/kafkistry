$(document).ready(function () {
    $("#refresh-btn").click(refreshCluster);
    $(".status-filter-btn").click(function () {
        let statusType = $(this).attr("data-status-type");
        filterDatatableBy(statusType);
    });
    maybeFilterDatatableByUrlHash();
    loadClusterIssues();
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

const CLUSTER_ISSUES_OP = "clusterIssues";

function loadClusterIssues() {
    let clusterIdentifier = $("meta[name=cluster-identifier]").attr("content");
    showOpProgressOnId(CLUSTER_ISSUES_OP, "Loading issues...");
    whenUrlSchemaReady(function () {
        let url = urlFor("clusters.showClusterIssues", {clusterIdentifier: clusterIdentifier});
        let resultContainer = $("#cluster-issues-result");
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (issues) {
                hideServerOpOnId(CLUSTER_ISSUES_OP);
                resultContainer.html(issues);
                refreshAllConfValuesIn(resultContainer);
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId(CLUSTER_ISSUES_OP);
                    resultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId(CLUSTER_ISSUES_OP, "Failed to get cluster iissues", errorMsg);
                }
            });
    });
}


