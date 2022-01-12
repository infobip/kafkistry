$(document).ready(function () {
    $("select").selectpicker();
    $("input[name=topicSelectType]").change(adjustTopicSelectType);
    $("#continue-throttle-btn").click(continueToThrottle);
});

function continueToThrottle() {
    let throttleRequest = extractThrottleRequest();
    console.log(JSON.stringify(throttleRequest));

    showOpProgress("Generating throttle configuration...");
    $
        .ajax("api/clusters-management-suggestion/submit-throttle-broker-topic-partitions", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(throttleRequest)
        })
        .done(function () {
            location.href = urlFor("topicsManagement.showThrottleBrokerPartitions");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Generating throttle configuration failed", errorMsg);
        });

}

function extractThrottleRequest() {
    let allTopics = $("select[name=topics] option").map((i, option) => {
        return $(option).attr("value");
    }).get();
    let selectedTopics = $("select[name=topics]").val();
    let topicSelectType = $("input[name=topicSelectType]:checked").val();
    let topics = [];
    switch (topicSelectType) {
        case "ALL":
            topics = allTopics;
            break;
        case "ONLY":
            topics = selectedTopics;
            break;
        case "NOT":
            topics  = allTopics.filter((topic) => { return selectedTopics.indexOf(topic) < 0; });
            break;
    }
    return {
        clusterIdentifier: $("meta[name=clusterIdentifier]").attr("content"),
        brokerIds: $("select[name=brokerIds]").val().map((brokerId) => {
            return parseInt(brokerId);
        }),
        topicNames: topics
    }
}

function adjustTopicSelectType() {
    let selectedVal = $(this).val();
    if (selectedVal === "ALL") {
        $("#topics-multi-select").hide();
    } else {
        $("#topics-multi-select").show();
    }
    $("#topic-select-type label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
}
