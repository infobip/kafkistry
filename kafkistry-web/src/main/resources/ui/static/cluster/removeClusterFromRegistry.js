$(document).ready(function () {
    $("#delete-btn").click(removeClusterFromRegistry);
});

function removeClusterFromRegistry() {
    let button = $(this);
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Removing cluster...");
    $
        .ajax("api/clusters?clusterIdentifier=" + encodeURI(clusterIdentifier) + "&message=removing", {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Cluster is successfully removed from registry");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Removal failed: " + errorMsg)
        });
}
