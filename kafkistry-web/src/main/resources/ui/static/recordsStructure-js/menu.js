$(document).ready(function () {
    $('select').on('change', updateInspectUrl);
});

function updateInspectUrl() {
    let clusterIdentifier = $("select[name=clusterIdentifier]").val();
    if (clusterIdentifier === "ALL") {
        clusterIdentifier = undefined;
    }
    let topicName = $("select[name=topicName]").val();
    let url = urlFor("recordsStructure.showTopicStructure", {
        clusterIdentifier: clusterIdentifier,
        topicName: topicName
    });
    console.log("updating inspect url to: " + url);
    $("#inspect-records-structure-btn").attr("href", url);
}

