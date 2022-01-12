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
    if (validateErr) {
        showOpError(validateErr);
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
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        if (allowDefaultMessage) {
            updateMsg = "initial creation";
        } else {
            showOpError("Please specify update message");
            return;
        }
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
