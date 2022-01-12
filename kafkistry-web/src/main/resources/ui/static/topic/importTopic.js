$(document).ready(function () {
    $("#import-btn").click(importTopic);
});

function importTopic() {
    let topicDescription = extractTopicDescription();
    createTopicDescription(topicDescription);
}
