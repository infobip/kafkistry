$(document).ready(function () {
    $("#delete-btn").click(deleteTopicInRegistry);
});

function deleteTopicInRegistry() {
    let button = $(this);
    let topicName = button.attr("data-topic-name");
    let deleteMessage = $("#delete-message").val();
    if (deleteMessage.trim() === "") {
        showOpError("Please specify delete reason");
        return;
    }
    showOpProgress("Deleting topic...");
    $
        .ajax("api/topics?topicName=" + encodeURI(topicName) + "&message=" + encodeURI(deleteMessage) + "&" + targetBranchUriParam(), {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Topic is successfully deleted from registry");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: " + errorMsg);
        });
}
