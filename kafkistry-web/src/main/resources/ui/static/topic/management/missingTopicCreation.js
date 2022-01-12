$(document).ready(function () {
    $("#create-missing-btn").click(createMissingTopic);
});

function createMissingTopic() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    showOpProgress("Creating missing topic...");
    $
        .ajax("api/management/create-missing-topic", {
            method: "POST",
            data: {
                topicName: topicName,
                clusterIdentifier: clusterIdentifier
            }
        })
        .done(function () {
            showOpSuccess("Creation completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}
