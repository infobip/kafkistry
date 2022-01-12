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
    iterableParamName, iterableAttrName
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
    processNext();
}

function processNext() {
    if (bulkIterationIndex >= bulkElementsList.length) {
        showOpSuccess(bulkOpName + " for all completed (" + bulkIterationIndex + "/" + bulkElementsList.length + ")");
    } else {
        let element = bulkElementsList[bulkIterationIndex];
        bulkIterationIndex++;
        processElement(element)
    }
}

function processElement(element) {
    let progressMsg = "(" + bulkIterationIndex + "/" + bulkElementsList.length + ")";
    showOpProgress(bulkOpName + " in progress... " + progressMsg);
    let statusId = "op-status-" + element.replace(/[:<>|;]/g, "_");
    showOpProgressOnId(statusId, bulkOpName + " for '" + element + "'... " + progressMsg);
    let url = bulkApiUrl;
    let ajaxData = undefined;
    switch (bulkHttpMethod) {
        case "GET":
        case "DELETE":
            url = bulkApiUrl +
                "?" + bulkCommonParamName + "=" + encodeURI(bulkCommonParamValue) +
                "&" + bulkIterableParamName + "=" + encodeURI(element);
            break;
        case "POST":
        case "PUT":
            ajaxData = {};
            ajaxData[bulkCommonParamName] = bulkCommonParamValue;
            ajaxData[bulkIterableParamName] = element;
            break;
        default:
            showOpErrorOnId(statusId, "Unable to perform api call", "Unsupported http method:" + bulkHttpMethod);
            return;
    }
    if (bulkHttpMethod)
        $
            .ajax(url, {
                method: bulkHttpMethod,
                data: ajaxData
            })
            .done(function (response, status, xhr) {
                showOpSuccessOnId(statusId, bulkOpName + " completed with success " + progressMsg + servedByMsg(xhr), response);
            })
            .fail(function (error) {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId(statusId, bulkOpName + " failed: " + progressMsg + servedByMsg(error), errorMsg);
            })
            .always(processNext);
}

function servedByMsg(xhr) {
    let serverHostname = xhr.getResponseHeader("Server-Hostname");
    if (serverHostname) {
        return " (served by: "+serverHostname+")"
    }
    return "";
}
