$(document).ready(function (){
    $(document).on("change", ".topic-selector", topicSelectorChanged);
    $("#preset-offsets-btn").click(presetConsumerOffsets);
});

let selectedTopics = {};

function topicSelectorChanged() {
    let checkbox = $(this);
    let topic = checkbox.attr("data-topic");
    let selected = checkbox.is(":checked");
    selectedTopics[topic] = !!selected;
    $("#topic-count").text(extractSelectedTopics().length);
}

function extractSelectedTopics() {
    return Object.keys(selectedTopics)
        .filter(function (topic) {
            return selectedTopics[topic];
        });
}

function extractPresetOffsets() {
    return {
        seek: extractOffsetSeek(),
        topics: extractSelectedTopics().map(function (topic) {
            return {
                topic: topic,
                partitions: null,
            }
        }),
    };
}

function presetConsumerOffsets() {
    showOpProgress("Initializing/presetting consumer group offsets...")
    let clusterIdentifier = $(this).attr("data-cluster");
    let consumerGroupId = $(this).attr("data-consumer-group");
    let reset = extractPresetOffsets();
    $
        .ajax("api/consumers/clusters/" + encodeURI(clusterIdentifier) + "/groups/" + encodeURI(consumerGroupId) + "/reset-offsets", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(reset)
        })
        .done(function (resetChange) {
            showOpSuccess("Successfully performed offset initialization", formatOffsetsChange(resetChange));
            $("#preset-group-link").show();
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpError("Got error while trying preset consumer group offsets", errMsg);
        });

}
