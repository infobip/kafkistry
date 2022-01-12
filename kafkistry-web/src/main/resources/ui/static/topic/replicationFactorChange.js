$(document).ready(function () {
    $("#assign-new-replicas-btn").click(assignNewPartitionReplicas);
    $("#remove-replicas-btn").click(removePartitionReplicas);
});

function assignNewPartitionReplicas() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    let throttleBytesPerSec = getThrottleBytesPerSec();
    showOpProgress("Adding topic partition replicas...");
    let partitions = getAssignmentsData(topicName);
    console.log("New assignment: " + JSON.stringify(partitions));
    console.log(partitions);
    $
        .ajax("api/management/add-topic-partitions-replicas" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier) +
            "&throttleBytesPerSec=" + throttleBytesPerSec,
            {
                method: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(partitions)
            }
        )
        .done(function () {
            showOpSuccess("New partition replicas assignment completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Partition replicas assignment failed:", errorMsg);
        });
}

function removePartitionReplicas() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Removing topic partition replicas...");
    let partitions = getAssignmentsData(topicName);
    console.log("New assignment: " + JSON.stringify(partitions));
    console.log(partitions);
    $
        .ajax("api/management/remove-topic-partitions-replicas" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier),
            {
                method: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(partitions)
            }
        )
        .done(function () {
            showOpSuccess("New partition replicas assignment completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Partition replicas assignment failed:", errorMsg);
        });
}
