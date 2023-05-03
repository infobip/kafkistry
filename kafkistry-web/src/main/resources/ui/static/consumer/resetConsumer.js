$(document).ready(function () {
    $("#reset-offsets-btn").click(resetOffsets);
});

function extractReset() {
    return {
        seek: extractOffsetSeek(),
        topics: extractTopicPartitions()
    };
}

function resetOffsets() {
    showOpProgress("Resetting consumer group offsets...")
    let clusterIdentifier = $(this).attr("data-cluster");
    let consumerGroupId = $(this).attr("data-consumer-group");
    let reset = extractReset();
    $
        .ajax("api/consumers/clusters/" + encodeURI(clusterIdentifier) + "/groups/" + encodeURI(consumerGroupId) + "/reset-offsets", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(reset)
        })
        .done(function (resetChange) {
            showOpSuccess("Successfully performed offset resets", formatOffsetsChange(resetChange));
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpError("Got error while trying reset consumer group offsets", errMsg);
        });
}
