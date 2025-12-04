$(document).ready(function () {
    $("#create-btn").click(function () {
        doModifyOperation(true, "Creating", "POST");
    });
    $("#import-btn").click(function () {
        doModifyOperation(true, "Importing", "POST");
    });
    $("#edit-btn").click(function () {
        doModifyOperation(false, "Editing", "PUT");
    });
    $("#delete-btn").click(function () {
        let quotaEntityID = $(this).attr("data-entity-id");
        doApiOperation(false, "Deleting", quotaEntityID, {
            method: "DELETE"
        });
    });
});

function doModifyOperation(allowDefaultMessage, opName, httpMethod) {
    let entityQuotas = extractEntityQuotas();
    let validateErr = validateEntityQuotas(entityQuotas);
    let targetBranchError = validateTargetBranch();
    let errors = [];
    if (validateErr) {
        errors.push(validateErr);
    }
    if (targetBranchError) {
        errors.push(targetBranchError);
    }
    if (errors.length > 0) {
        showOpError(errors.join("\n"));
        return;
    }
    let quotaEntityID = entityIdOf(entityQuotas.entity);
    doApiOperation(allowDefaultMessage, opName, quotaEntityID, {
        method: httpMethod,
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(entityQuotas)
    })
}

function doApiOperation(allowDefaultMessage, opName, quotaEntityID, ajaxOptions) {
    let targetBranchError = validateTargetBranch();
    let updateMsg = extractUpdateMessage();
    let errors = [];
    if (targetBranchError) {
        errors.push(targetBranchError);
    }
    if (updateMsg.trim() === "") {
        if (allowDefaultMessage) {
            updateMsg = "initial creation";
        } else {
            errors.push("Please specify update message");
        }
    }
    if (errors.length > 0) {
        showOpError(errors.join("\n"));
        return;
    }
    showOpProgress(opName + " entity quotas...");
    let url = "api/quotas?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam();
    if (ajaxOptions.method === "DELETE") {
        url += "&quotaEntityID="+encodeURI(quotaEntityID);
    }
    $.ajax(url, ajaxOptions)
        .done(function () {
            showOpSuccess(opName + " completed with success, redirecting...");
            setTimeout(function () {
                $.get("api/quotas/single?quotaEntityID=" + encodeURI(quotaEntityID))
                    .done(function (response) {
                        location.href = urlFor("quotas.showEntity", {quotaEntityID: quotaEntityID})
                    })
                    .fail(function (error) {
                        location.href = urlFor("quotas.showAll");
                    })
            }, 1000);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError(opName + " entity quotas failed", errorMsg);
        });
}
