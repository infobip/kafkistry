$(document).ready(function () {
    $("#bulk-update-topics-configs-btn").click(bulkUpdateTopicsConfigs);
});

function bulkUpdateTopicsConfigs() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Updating topics configs on cluster", "wrong-config-topic", "POST",
        "api/management/update-topic-to-config",
        "clusterIdentifier", clusterIdentifier,
        "topicName", "data-topic-name",
        function (topicName) {
            return extractTopicConfigUpdates(topicName, clusterIdentifier);
        }
    );
}
