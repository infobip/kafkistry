$(document).ready(function () {
    $("#bulk-delete-where-unwanted-btn").click(bulkDeleteWhereUnwantedTopics);
});

function bulkDeleteWhereUnwantedTopics() {
    if (!verifyBulkDeleteConfirm()) {
        return;
    }
    let topicName = $(this).attr("data-topic-name");
    performBulkOperations(
        "Topic deletion", "unwanted-topic", "DELETE",
        "api/management/delete-topic",
        "topicName", topicName,
        "clusterIdentifier", "data-cluster-identifier"
    );
}

function verifyBulkDeleteConfirm() {
    let text = $("#bulk-delete-confirm").val();
    if (text !== "DELETE") {
        showOpError("You did not confirm deletion by entering DELETE correctly");
        return false;
    } else {
        return true;
    }
}
