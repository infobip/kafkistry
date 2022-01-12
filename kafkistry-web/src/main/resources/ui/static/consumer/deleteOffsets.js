$(document).ready(function () {
    $("#delete-offsets-btn").click(deleteOffsets);
});

function extractTopicPartitionsToDelete() {
    let topicsPartitionsArr = extractTopicPartitions();
    let topicPartitions = {};
    topicsPartitionsArr.forEach(function (topicPartitionsArr) {
        let topic = topicPartitionsArr.topic;
        let partitions = [];
        topicPartitionsArr.partitions.forEach(function (partitionsArr) {
            partitions.push(partitionsArr.partition);
        });
        topicPartitions[topic] = partitions;
    });
    return topicPartitions;
}
function deleteOffsets() {
    showOpProgress("Deleting consumer group offsets...");
    let clusterIdentifier = $(this).attr("data-cluster");
    let consumerGroupId = $(this).attr("data-consumer-group");
    let topicPartitions = extractTopicPartitionsToDelete();
    showOpSuccess("Extracted", "DC: "+clusterIdentifier+"\nGroup: "+consumerGroupId+"\n"+JSON.stringify(topicPartitions, null, 4))
    $
        .ajax("api/consumers/clusters/" + encodeURI(clusterIdentifier) + "/groups/" + encodeURI(consumerGroupId) + "/offsets/delete", {
            method: "PUT",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicPartitions)
        })
        .done(function () {
            showOpSuccess("Successfully deleted consumer group offsets");
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpError("Got error while trying delete consumer group offsets", errMsg);
        });
}


