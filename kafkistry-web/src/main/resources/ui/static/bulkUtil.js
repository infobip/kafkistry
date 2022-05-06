let bulkIterationIndex = null;
let bulkElementsList = null;

let bulkHttpMethod = null;
let bulkApiUrl = null;
let bulkCommonParamName = null;
let bulkCommonParamValue = null;
let bulkIterableParamName = null;
let bulkOpName = null;

function performBulkOperations(
    opName, elementsClass, httpMethod, apiUrl,
    commonParamName, commonParamValue,
    iterableParamName, iterableAttrName,
    dataSupplier,
) {
    bulkHttpMethod = httpMethod;
    bulkApiUrl = apiUrl;
    bulkCommonParamName = commonParamName;
    bulkCommonParamValue = commonParamValue;
    bulkIterableParamName = iterableParamName;
    bulkOpName = opName;
    bulkElementsList = [];
    $("." + elementsClass).each(function () {
        let element = $(this).attr(iterableAttrName);
        bulkElementsList.push(element);
    });
    showOpProgress(bulkOpName + " for all... (0/" + bulkElementsList.length + ")");
    console.log("bulk elements:");
    console.log(bulkElementsList);
    bulkIterationIndex = 0;
    processNext(dataSupplier);
}

function processNext(dataSupplier) {
    if (bulkIterationIndex >= bulkElementsList.length) {
        showOpSuccess(bulkOpName + " for all completed (" + bulkIterationIndex + "/" + bulkElementsList.length + ")");
    } else {
        let element = bulkElementsList[bulkIterationIndex];
        bulkIterationIndex++;
        processElement(element, dataSupplier)
    }
}

function processElement(element, dataSupplier) {
    let progressMsg = "(" + bulkIterationIndex + "/" + bulkElementsList.length + ")";
    showOpProgress(bulkOpName + " in progress... " + progressMsg);
    let statusId = "op-status-" + element.replace(/[:<>|;]/g, "_");
    showOpProgressOnId(statusId, bulkOpName + " for '" + element + "'... " + progressMsg);
    let url = bulkApiUrl +
        "?" + bulkCommonParamName + "=" + encodeURI(bulkCommonParamValue) +
        "&" + bulkIterableParamName + "=" + encodeURI(element);
    let contentType = undefined;
    let ajaxData = undefined;
    switch (bulkHttpMethod) {
        case "GET":
        case "DELETE":
            break;
        case "POST":
        case "PUT":
            contentType = "application/json; charset=utf-8";
            ajaxData = {};
            break;
        default:
            showOpErrorOnId(statusId, "Unable to perform api call", "Unsupported http method:" + bulkHttpMethod);
            return;
    }
    if (dataSupplier) {
        ajaxData = dataSupplier(element);
    }
    if (bulkHttpMethod)
        $
            .ajax(url, {
                method: bulkHttpMethod,
                contentType: contentType,
                data: ajaxData ? JSON.stringify(ajaxData) : undefined,
            })
            .done(function (response, status, xhr) {
                showOpSuccessOnId(statusId, bulkOpName + " completed with success " + progressMsg + servedByMsg(xhr), response);
            })
            .fail(function (error) {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId(statusId, bulkOpName + " failed: " + progressMsg + servedByMsg(error), errorMsg);
            })
            .always(function () {
                processNext(dataSupplier);
            });
}

function servedByMsg(xhr) {
    let serverHostname = xhr.getResponseHeader("Server-Hostname");
    if (serverHostname) {
        return " (served by: "+serverHostname+")"
    }
    return "";
}
