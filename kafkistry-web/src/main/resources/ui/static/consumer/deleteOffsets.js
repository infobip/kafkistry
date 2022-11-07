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

function selectedTopicPartitionsMsg(topicPartitions) {
    let topics = Object.keys(topicPartitions);
    let extractedPartitionsMsg = "Topics (" + topics.length + "):\n";
    if (topics.length === 0) {
        extractedPartitionsMsg += " (none)\n";
    }
    topics.forEach(function (topic) {
        extractedPartitionsMsg += " - " + topic + "\n";
        let partitions = topicPartitions[topic];
        extractedPartitionsMsg += "   - partitions (" + partitions.length + "): " + partitions.join(", ") + "\n";
    });
    return extractedPartitionsMsg;
}

function deleteOffsets() {
    showOpProgress("Deleting consumer group offsets...");
    let clusterIdentifier = $(this).attr("data-cluster");
    let consumerGroupId = $(this).attr("data-consumer-group");
    let topicPartitions = extractTopicPartitionsToDelete();
    let extractedPartitionsMsg = selectedTopicPartitionsMsg(topicPartitions);
    showOpProgress("Deleting consumer group offsets...",
        "Kafka cluster: " + clusterIdentifier + "\nGroup: " + consumerGroupId + "\n" + extractedPartitionsMsg
    );
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


