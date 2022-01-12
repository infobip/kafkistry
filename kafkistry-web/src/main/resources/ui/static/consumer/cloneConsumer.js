$(document).ready(function () {
    $("#clone-offsets-btn").click(cloneOffsets);
});

function extractReset(fromConsumerGroupId) {
    return {
        seek: {
            type: "CLONE",
            cloneFromConsumerGroup: fromConsumerGroupId
        },
        topics: extractTopicPartitions()
    };
}

function cloneOffsets() {
    showOpProgress("Cloning consumer group offsets...")
    let clusterIdentifier = $(this).attr("data-cluster");
    let fromConsumerGroupId = $(this).attr("data-from-consumer-group");
    let intoConsumerGroupId = $(this).attr("data-into-consumer-group");
    let reset = extractReset(fromConsumerGroupId);
    $
        .ajax("api/consumers/clusters/" + encodeURI(clusterIdentifier) + "/groups/" + encodeURI(intoConsumerGroupId) + "/reset-offsets", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(reset)
        })
        .done(function (resetChange) {
            showOpSuccess("Successfully performed offset cloning", formatOffsetsChange(resetChange));
            $("#into-group-link").show();
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpError("Got error while trying to clone consumer group offsets", errMsg);
        });
}


