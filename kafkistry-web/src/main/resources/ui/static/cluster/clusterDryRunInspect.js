$(document).ready(function () {
    $("#cluster-dry-run-inspect-btn").click(clusterDryRunInspect);
});

let CLUSTER_DRY_RUN_INSPECT_OP = "cluster-dry-run-inspect"

function clusterDryRunInspect() {
    console.log("going to dry-run inspect cluster");
    showOpProgressOnId(CLUSTER_DRY_RUN_INSPECT_OP, "Dry-run inspecting cluster...")
    let kafkaCluster = extractClusterData();
    $
        .ajax(urlFor("clusters.showClusterDryRunInspect"), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(kafkaCluster)
        })
        .done(function (response) {
            hideServerOpOnId(CLUSTER_DRY_RUN_INSPECT_OP);
            $("#cluster-dry-run-inspect-result").html(response);
            registerAllInfoTooltips();
            refreshAllConfValues();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId(CLUSTER_DRY_RUN_INSPECT_OP);
                $("#cluster-dry-run-inspect-result").html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId(CLUSTER_DRY_RUN_INSPECT_OP, "Dry run of cluster inspect failed", errorMsg);
            }
        });
}