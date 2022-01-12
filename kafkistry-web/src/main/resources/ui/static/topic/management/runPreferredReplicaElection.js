$(document).ready(function () {
    $("#run-preferred-replica-election-btn").click(runPreferredReplicasElection);
    $("#bulk-run-preferred-replica-election-btn").click(bulkRunPreferredReplicasElection);
});

function runPreferredReplicasElection() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Running preferred replica leader elections...");
    $
        .post("api/management/run-preferred-replica-elections" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier)
        )
        .done(function (response) {
            showOpSuccess("Elections completed");
        })
        .fail(function(error){
            let errorMsg = extractErrMsg(error);
            showOpError("Failed to complete leader elections:", errorMsg);
        });
}

function bulkRunPreferredReplicasElection() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Re-electing topic partition leaders", "re-elect-topic", "POST",
        "api/management/run-preferred-replica-elections",
        "clusterIdentifier", clusterIdentifier,
        "topicName", "data-topic-name"
    );
}