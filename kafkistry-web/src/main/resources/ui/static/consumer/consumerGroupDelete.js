$(document).ready(function () {
    $("#deleteConsumerGroupBtn").click(deleteConsumerGroup);
});

function deleteConsumerGroup() {
    let btn = $(this);
    if (!verifyDeleteConfirm()) {
        return;
    }
    let clusterIdentifier = btn.attr("data-clusterIdentifier");
    let consumerGroupId = btn.attr("data-consumerGroupId");

    showOpProgress("Deleting consumer group on cluster...");
    let urlPath = "api/consumers/clusters/" + clusterIdentifier + "/groups/" + consumerGroupId;
    $
        .ajax(urlPath, {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Deletion completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: " + errorMsg);
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
