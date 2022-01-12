$(document).ready(function () {
    $("input.conf-value-in").on("change, keypress, click, input", null, null, refreshDiff);
    refreshDiff();
    $("#topic-config-set-btn").click(topicConfigSet);
});

function extractNewConfig() {
    let config = {};
    $("input.conf-value-in").each(function () {
       let entry = $(this);
       config[entry.attr("name")] = entry.val();
    });
    return config;
}

function extractCurrentConfig() {
    let config = {};
    $(".conf-value").each(function () {
       let entry = $(this);
       config[entry.attr("data-name")] = entry.attr("data-value");
    });
    return config;
}

function refreshDiff() {
    let currentConfig = extractCurrentConfig();
    let newConfig = extractNewConfig();
    Object.keys(newConfig).forEach(function (key) {
       let currentValue = currentConfig[key];
       let newValue = newConfig[key];
       let diffElement = $(".config-change[data-name='"+key+"']")
       if (newValue === currentValue) {
           diffElement.text("---");
       } else {
           let diffHtml = generateDiffHtml(currentValue, newValue);
           diffElement.html(diffHtml);
       }
    });
}

function topicConfigSet() {
    showOpProgress("Setting config...");
    let button = $(this);
    let clusterIdentifier = button.attr("data-clusterIdentifier");
    let topic = button.attr("data-topic");
    let topicConfig = extractNewConfig();
    $
        .ajax("api/management/set-topic-config"
            + "?clusterIdentifier=" + encodeURI(clusterIdentifier)
            + "&topicName=" + encodeURI(topic), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicConfig)
        })
        .done(function () {
            showOpSuccess("Successfully set config");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Failed to set config", errorMsg);
        });

}