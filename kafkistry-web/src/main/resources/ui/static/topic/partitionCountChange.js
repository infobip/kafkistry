$(document).ready(function () {
    $("#add-partitions-btn").click(partitionCountChange);
});

function partitionCountChange() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Adding topic partitions...");
    let newPartitions = getAssignmentsData(topicName);
    console.log("New partitions: " + JSON.stringify(newPartitions));
    console.log(newPartitions);
    $
        .ajax("api/management/add-topic-partitions?topicName=" + encodeURI(topicName) + "&clusterIdentifier=" + encodeURI(clusterIdentifier), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(newPartitions)
        })
        .done(function () {
            showOpSuccess("New partitions creation completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Partitions creation failed: " + errorMsg);
        });
}
