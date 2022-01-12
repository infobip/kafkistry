$(document).ready(function () {
    $("#save-btn").click(editPrincipalAcls);
});

function editPrincipalAcls() {
    let principalAcls = extractPrincipalAcls();
    doEditPrincipalAcls(principalAcls);
}

function doEditPrincipalAcls(principalAcls) {
    let validateErr = validatePrincipalAcls(principalAcls);
    if (validateErr) {
        showOpError(validateErr);
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        showOpError("Please specify update reason");
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