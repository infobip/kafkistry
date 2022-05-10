$(document).ready(function () {
    $("#edit-cluster-btn").click(editCluster);
});

function editCluster() {
    showOpProgress("Editing cluster...");
    let cluster = extractClusterData();
    let validateErr = validateCluster(cluster);
    if (validateErr) {
        showOpError(validateErr);
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        showOpError("Please specify update reason");
        return;
    }
    $
        .ajax("api/clusters?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "PUT",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(cluster)
        })
        .done(function () {
            showOpSuccess("Successfully edited cluster");
            setTimeout(function () {
                location.href = urlFor("clusters.showCluster", {clusterIdentifier: cluster.identifier});
            }, 1000);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Edit failed: " + errorMsg);
        });
}