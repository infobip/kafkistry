$(document).ready(function () {
    $("#bulk-update-topic-config-btn").click(bulkUpdateTopicConfig);
});

function bulkUpdateTopicConfig() {
    let topicName = $(this).attr("data-topic-name");
    performBulkOperations(
        "Updating topic config on clusters", "wrong-config-topic", "POST",
        "api/management/update-topic-to-config",
        "topicName", topicName,
        "clusterIdentifier", "data-cluster-identifier",
        function (clusterIdentifier) {
            return extractTopicConfigUpdates(topicName, clusterIdentifier);
        }
    );
}
