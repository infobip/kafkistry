$(document).ready(function () {
    $("#cancel-reassignments-btn").click(cancelReAssignments);
});

function cancelReAssignments() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Canceling re-assignments...");
    $
        .ajax("api/management/cancel-topic-partitions-reassignment" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier), {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Canceled re-assignment successfully");
        })
        .fail(function(error){
            let errorMsg = extractErrMsg(error);
            showOpError("Failed to cancel re-assignment:", errorMsg);
        });
}