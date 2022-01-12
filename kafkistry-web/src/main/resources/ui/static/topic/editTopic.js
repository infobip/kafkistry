$(document).ready(function () {
    $("#edit-btn").click(editTopic);
});

function editTopic() {
    let topicDescription = extractTopicDescription();
    let validateErr = validateTopicDescription(topicDescription);
    if (validateErr) {
        showOpError(validateErr);
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        showOpError("Please specify update reason");
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
