$(document).ready(function () {
    loadTopicDescriptionYaml();
});

function loadTopicDescriptionYaml() {
    let topicYaml = $("#topic-yaml");
    let topicName = topicYaml.attr("data-topic-name");
    $.get("api/topics/single", {topicName: topicName})
        .done(function (topicDescription) {
            jsonToYaml(topicDescription, function (yaml) {
                $("#filename").text("topics/" + topicDescription.name.replace(/[^\w\-.]/, "_") + ".yaml");
                topicYaml.text(yaml);
            });
        });
}
