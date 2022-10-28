$(document).ready(function () {
    $("#cluster-dry-run-inspect-btn").click(clusterDryRunInspect);
});

let CLUSTER_DRY_RUN_INSPECT_OP = "cluster-dry-run-inspect"

function clusterDryRunInspect() {
    doClusterDryRunInspect(
        CLUSTER_DRY_RUN_INSPECT_OP,
        $("#cluster-dry-run-inspect-result"),
        $("#cluster-dry-run-inspect-summary"),
        extractClusterData
    );
}

function doClusterDryRunInspect(opStatusId, resultContainer, summaryContainer, clusterDataSupplier) {
    console.log("going to dry-run inspect cluster");
    showOpProgressOnId(opStatusId, "Dry-run inspecting cluster...")
    summaryContainer.hide();
    let kafkaCluster = clusterDataSupplier();
    kafkaClusterDryRunInspected = kafkaCluster;
    $
        .ajax(urlFor("clusters.showClusterDryRunInspect"), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(kafkaCluster)
        })
        .done(function (response) {
            hideServerOpOnId(opStatusId);
            resultContainer.html(response);
            registerAllInfoTooltipsIn(resultContainer);
            refreshAllConfValuesIn(resultContainer);
            initDatatablesIn(resultContainer);
            summaryContainer.html(resultContainer.find(".inspect-summary").html());
            summaryContainer.show();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId(opStatusId);
                resultContainer.html(errHtml);
                summaryContainer.html("<span class='badge bg-danger text-white'>FAILED</span>");
                summaryContainer.show();
            } else {
                let errorMsg = extractErrMsg(error);
                summaryContainer.hide();
                showOpErrorOnId(opStatusId, "Dry run of cluster inspect failed", errorMsg);
            }
        });
}