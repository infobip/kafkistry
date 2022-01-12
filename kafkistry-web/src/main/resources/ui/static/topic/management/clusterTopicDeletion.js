$(document).ready(function () {
    $("#delete-topic-btn").click(deleteTopicOnCluster);
});

function deleteTopicOnCluster() {
    let button = $(this);
    if (!verifyDeleteConfirm()) {
        return;
    }
    let topicName = button.attr("data-topic-name");
    let clusterIdentifier = button.attr("data-cluster-identifier");
    let forceDelete = $("#force-delete").is(":checked");
    showOpProgress("Deleting topic on cluster...");
    let urlPath = forceDelete ? "api/management/force-delete-topic" : "api/management/delete-topic";
    $
        .ajax(urlPath + "?topicName=" + encodeURI(topicName) + "&clusterIdentifier=" + encodeURI(clusterIdentifier), {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Deletion completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: "+ errorMsg);
        });
}

function verifyDeleteConfirm() {
    let text = $("#delete-confirm").val();
    if (text !== "DELETE") {
        showOpError("You did not confirm deletion by entering DELETE correctly");
        return false;
    } else {
        return true;
    }
}
