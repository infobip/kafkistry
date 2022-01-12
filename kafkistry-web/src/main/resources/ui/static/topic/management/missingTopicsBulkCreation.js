$(document).ready(function () {
    $("#bulk-create-missing-btn").click(bulkCreateMissingTopics);
});

function bulkCreateMissingTopics() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Creating topic", "missing-topic", "POST",
        "api/management/create-missing-topic",
        "clusterIdentifier", clusterIdentifier,
        "topicName", "data-topic-name"
    );
}