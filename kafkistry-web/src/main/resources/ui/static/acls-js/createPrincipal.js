$(document).ready(function () {
    $("#create-btn").click(createPrincipalAcls);
});

function createPrincipalAcls() {
    let principalAcls = extractPrincipalAcls();
    doCreatePrincipalAcls(principalAcls);
}

function doCreatePrincipalAcls(principalAcls) {
    let validateErr = validatePrincipalAcls(principalAcls);
    if (validateErr) {
        showOpError(validateErr);
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        updateMsg = "initial creation";
    }
    updateMsg = appendJiraIssuesIfAny(updateMsg, principalAcls.description);
    showOpProgress("Creating principal ACLs...");
    $
        .ajax("api/acls?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(principalAcls)
        })
        .done(function () {
            showOpSuccess("Creation completed with success, redirecting...");
            setTimeout(function () {
                $.get("api/acls/single?principal=" + encodeURI(principalAcls.principal))
                    .done(function () {
                        location.href = urlFor("acls.showAllPrincipalAcls", {principal: principalAcls.principal})
                    })
                    .fail(function () {
                        location.href = urlFor("acls.showAll");
                    })
            }, 1000);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}