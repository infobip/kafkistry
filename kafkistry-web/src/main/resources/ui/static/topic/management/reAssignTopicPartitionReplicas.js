$(document).ready(function () {
    $("#apply-re-assignments-btn").click(applyReAssignments);
    $("#apply-bulk-re-assignments-btn").click(applyBulkReAssignments);
});

function applyReAssignments() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    let throttleBytesPerSec = getThrottleBytesPerSec();
    showOpProgress("Applying re-assignments...");
    let assignments = getAssignmentsData(topicName);
    console.log("New assignments: " + JSON.stringify(assignments));
    console.log(assignments);
    $
        .ajax("api/management/apply-topic-partitions-reassignment" +
            "?topicName=" + encodeURI(topicName) +
            "&clusterIdentifier=" + encodeURI(clusterIdentifier) +
            "&throttleBytesPerSec=" + throttleBytesPerSec,
            {
                method: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(assignments)
            }
        )
        .done(function () {
            showOpSuccess("New partition assignments applied with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Partition re-assignment failed:", errorMsg);
        });
}

function applyBulkReAssignments() {
    let button = $(this);
    let clusterIdentifier = button.attr("data-cluster-identifier");
    let throttleBytesPerSec = getThrottleBytesPerSec();
    showOpProgress("Applying re-assignments...");

    let topicsAssignmentData = {};
    $(".topic-assignment-data").map(function () {
        let topicName = $(this).attr("data-topic-name");
        topicsAssignmentData[topicName] = getAssignmentsData(topicName);
    });

    console.log("New assignments: " + JSON.stringify(topicsAssignmentData));
    console.log(topicsAssignmentData);

    $
        .ajax("api/clusters-management/apply-topics-bulk-re-assignments" +
            "?clusterIdentifier=" + encodeURI(clusterIdentifier) +
            "&throttleBytesPerSec=" + throttleBytesPerSec,
            {
                method: "POST",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify(topicsAssignmentData)
            }
        )
        .done(function () {
            showOpSuccess("New partitions assignments applied with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Partition re-assignment failed:", errorMsg);
        });
}
