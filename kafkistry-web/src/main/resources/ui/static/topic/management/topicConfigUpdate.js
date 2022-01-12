$(document).ready(function () {
    $("#update-topic-config-btn").click(updateTopicConfig);
});

function updateTopicConfig() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Updating topic config...");
    $
        .ajax("api/management/update-topic-to-config", {
            method: "POST",
            data: {
                topicName: topicName,
                clusterIdentifier: clusterIdentifier
            }
        })
        .done(function () {
            showOpSuccess("Config update completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Config update failed: " + errorMsg);
        });
}
