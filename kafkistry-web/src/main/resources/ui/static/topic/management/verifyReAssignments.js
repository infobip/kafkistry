$(document).ready(function () {
    $("#verify-reassignments-btn").click(verifyReAssignments);
    $("#bulk-verify-re-assignments-btn").click(bulkVerifyReAssignments);
});

function verifyReAssignments() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Verifying re-assignments...");
    $
        .get("api/management/verify-topic-partitions-reassignment" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier)
        )
        .done(function (response) {
            showOpSuccess("Verified", response);
        })
        .fail(function(error){
            let errorMsg = extractErrMsg(error);
            showOpError("Failed to verify:", errorMsg);
        });
}

function bulkVerifyReAssignments() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Verifying topic assignments", "verify-re-assignment-topic", "GET",
        "api/management/verify-topic-partitions-reassignment",
        "clusterIdentifier", clusterIdentifier,
        "topicName", "data-topic-name"
    );
}