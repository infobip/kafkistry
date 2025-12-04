$(document).ready(function () {
    $("#create-btn").click(createTopic);
});

function createTopic() {
    let topicDescription = extractTopicDescription();
    createTopicDescription(topicDescription);
}

function createTopicDescription(topicDescription) {
    let validateErrors = validateTopicDescription(topicDescription);
    let targetBranchError = validateTargetBranch();
    if (targetBranchError) {
        validateErrors.push(targetBranchError);
    }
    if (validateErrors.length > 0) {
        showOpError("Failed validation", validateErrors.join("\n"));
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        updateMsg = "initial creation";
    }
    updateMsg = appendJiraIssuesIfAny(updateMsg, topicDescription.description);
    showOpProgress("Creating topic...");
    $
        .ajax("api/topics?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicDescription)
        })
        .done(function () {
            showOpSuccess("Creation completed with success, redirecting...");
            setTimeout(function () {
                $.get("api/topics/single?topicName=" + encodeURI(topicDescription.name))
                    .done(function () {
                        location.href = urlFor("topics.showTopic", {topicName: topicDescription.name})
                    })
                    .fail(function () {
                        location.href = urlFor("topics.showTopics");
                    })
            }, 1000);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}
