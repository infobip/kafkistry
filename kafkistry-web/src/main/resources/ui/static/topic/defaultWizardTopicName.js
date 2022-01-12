$(document).ready(function () {
    $(".topicName").on("change, keypress, click, input", "input", null, refreshGeneratedTopicName);
});

function extractTopicNameMetadata() {
    let topicName = $("input[name='topicName']").val().trim();
    return {
        attributes: {
            name: topicName
        }
    }
}

function validateTopicMetadata(topicNameMeta) {
    if (!topicNameMeta.attributes.name)
        return "Topic name must be specified";
    return false;
}



