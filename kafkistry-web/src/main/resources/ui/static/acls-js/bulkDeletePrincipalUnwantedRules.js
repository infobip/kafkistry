$(document).ready(function () {
    $("#bulk-delete-principal-rules-btn").click(bulkDeletePrincipalUnwantedAcls);
});

function bulkDeletePrincipalUnwantedAcls() {
    if (!verifyDeleteConfirm()) {
        return;
    }
    let principal = $(this).attr("data-principal");
    performBulkOperations(
        "Deleting", "unwanted-rules-cluster", "DELETE",
        "api/acls-management/delete-principal-acls",
        "principal", principal,
        "clusterIdentifier", "data-cluster-identifier"
    );
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
