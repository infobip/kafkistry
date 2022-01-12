$(document).ready(function () {
    $("#delete-principal-btn").click(deletePrincipalAcls);
});

function deletePrincipalAcls() {
    let principal = $(this).attr("data-principal");
    doDeletePrincipalAcls(principal);
}

function doDeletePrincipalAcls(principal) {
    let deleteMessage = $("#delete-message").val();
    if (deleteMessage.trim() === "") {
        showOpError("Please specify delete reason");
        return;
    }
    showOpProgress("Deleting topic...");
    $
        .ajax("api/acls?principal=" + encodeURI(principal) + "&message=" + encodeURI(deleteMessage) + "&" + targetBranchUriParam(), {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Principal is successfully deleted from registry");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: " + errorMsg);
        });
}