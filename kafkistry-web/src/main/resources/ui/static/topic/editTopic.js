$(document).ready(function () {
    $("#edit-btn").click(editTopic);
});

function editTopic() {
    let topicDescription = extractTopicDescription();
    let validateErrors = validateTopicDescription(topicDescription);
    let targetBranchError = validateTargetBranch();
    if (targetBranchError) {
        validateErrors.push(targetBranchError);
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        validateErrors.push("Please specify update reason");
    }
    if (validateErrors.length > 0) {
        showOpError("Failed validation", validateErrors.join("\n"));
        return;
    }
    updateMsg = appendJiraIssuesIfAny(updateMsg, topicDescription.description);
    showOpProgress("Editing topic...");
    $
        .ajax("api/topics?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "PUT",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicDescription)
        })
        .done(function () {
            location.href = urlFor("topics.showTopic", {topicName: topicDescription.name});
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError(errorMsg);
        });
}
