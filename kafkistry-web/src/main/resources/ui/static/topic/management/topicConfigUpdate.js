$(document).ready(function () {
    $("#update-topic-config-btn").click(updateTopicConfig);
});

function updateTopicConfig() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    let topicConfigToSet = extractTopicConfigUpdates();
    showOpProgress("Updating topic config...");
    $
        .ajax("api/management/update-topic-to-config" +
            "?topicName="+encodeURIComponent(topicName) +
            "&clusterIdentifier="+encodeURIComponent(clusterIdentifier), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicConfigToSet)
        })
        .done(function () {
            showOpSuccess("Config update completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Config update failed: " + errorMsg);
        });
}

function extractTopicConfigUpdates() {
    let configValues = {};
    $(".new-value").each(function () {
        let value = $(this).attr("data-value");
        let key = $(this).attr("data-name");
        let isNull = $(this).attr("data-is-null") === "true";
        configValues[key] = isNull ? null : value;
    });
    return configValues;
}