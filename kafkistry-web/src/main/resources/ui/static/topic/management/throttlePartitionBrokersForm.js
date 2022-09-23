$(document).ready(function () {
    $(document).on("change", ".topic-checkbox", null, topicCheckboxChanged);
    $("select").selectpicker();
    $("input[name=topicSelectType]").change(adjustTopicSelectType);
    allTopics = $(".topic-name").map(function (){
        return $(this).attr("data-topic");
    }).get();
    $("#selected-topics-count").text(allTopics.length);
    let currentSelectType = $("[name=topicSelectType]:checked").val();
    adjustTopicSelectType.call($("[name=topicSelectType][value="+ currentSelectType+"]").get());
    $(".topic-checkbox").attr("checked", false);
    $("#continue-throttle-btn").click(continueToThrottle);
});

let allTopics;
let selectedTopics = [];

function topicCheckboxChanged() {
    let checked = $(this).is(":checked");
    let topic = $(this).attr("data-topic");
    $(this).closest("td").attr("data-order", checked ? 1 : 0);
    $("#topics-table").DataTable()
        .row($(this).closest("tr"))
        .invalidate();
    let indexInSelected = selectedTopics.indexOf(topic);
    if (checked) {
        if (indexInSelected < 0) selectedTopics.push(topic);
    } else {
        if (indexInSelected >= 0) selectedTopics.splice(indexInSelected, 1);
    }
    setTopicsCount(selectedTopics.length);
    console.log("topic: "+topic+" checked: "+checked);
}

function setTopicsCount(count) {
    $("#selected-topics-count").text(count);
}

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
    let topicSelectType = $("input[name=topicSelectType]:checked").val();
    let topics = [];
    switch (topicSelectType) {
        case "ALL":
            topics = allTopics;
            break;
        case "CUSTOM":
            topics = selectedTopics;
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
        setTopicsCount(allTopics.length);
    } else {
        $("#topics-multi-select").show();
        $("#topics-table").DataTable().draw();
        setTopicsCount(selectedTopics.length);
    }
    $("#topic-select-type label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
    console.log("initialized select type="+selectedVal);
}
