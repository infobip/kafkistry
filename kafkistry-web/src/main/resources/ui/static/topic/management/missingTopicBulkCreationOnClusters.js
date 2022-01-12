$(document).ready(function () {
    $("#bulk-create-where-missing-btn").click(bulkCreateWhereMissingTopics);
});

function bulkCreateWhereMissingTopics() {
    let topicName = $(this).attr("data-topic-name");
    performBulkOperations(
        "Topic creation", "missing-topic", "POST",
        "api/management/create-missing-topic",
        "topicName", topicName,
        "clusterIdentifier", "data-cluster-identifier"
    );
}
