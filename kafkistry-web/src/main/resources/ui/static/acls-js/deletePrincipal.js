$(document).ready(function () {
    $("#delete-principal-btn").click(deletePrincipalAcls);
});

function deletePrincipalAcls() {
    let principal = $(this).attr("data-principal");
    doDeletePrincipalAcls(principal);
}

function doDeletePrincipalAcls(principal) {
    let targetBranchError = validateTargetBranch();
    let deleteMessage = $("#delete-message").val();
    let errors = [];
    if (targetBranchError) {
        errors.push(targetBranchError);
    }
    if (deleteMessage.trim() === "") {
        errors.push("Please specify delete reason");
    }
    if (errors.length > 0) {
        showOpError(errors.join("\n"));
        return;
    }
    showOpProgress("Deleting principal ACLs...");
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