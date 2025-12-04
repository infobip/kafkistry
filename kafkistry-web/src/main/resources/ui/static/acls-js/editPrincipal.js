$(document).ready(function () {
    $("#save-btn").click(editPrincipalAcls);
});

function editPrincipalAcls() {
    let principalAcls = extractPrincipalAcls();
    doEditPrincipalAcls(principalAcls);
}

function doEditPrincipalAcls(principalAcls) {
    let validateErr = validatePrincipalAcls(principalAcls);
    let targetBranchError = validateTargetBranch();
    let updateMsg = extractUpdateMessage();
    let errors = [];
    if (validateErr) {
        errors.push(validateErr);
    }
    if (targetBranchError) {
        errors.push(targetBranchError);
    }
    if (updateMsg.trim() === "") {
        errors.push("Please specify update reason");
    }
    if (errors.length > 0) {
        showOpError(errors.join("\n"));
        return;
    }
    updateMsg = appendJiraIssuesIfAny(updateMsg, principalAcls.description);
    showOpProgress("Saving principal ACLs...");
    $
        .ajax("api/acls?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "PUT",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(principalAcls)
        })
        .done(function () {
            location.href = urlFor("acls.showAllPrincipalAcls", {principal: principalAcls.principal});
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}