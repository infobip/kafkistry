function hideOpStatus() {
    hideServerOpOnId("server-op-status");
}

function showOpProgress(message, preMessage) {
    showOpProgressOnId("server-op-status", message, preMessage);
}

function showOpSuccess(message, preMessage) {
    showOpSuccessOnId("server-op-status", message, preMessage);
}

function showOpError(message, preMessage) {
    showOpErrorOnId("server-op-status", message, preMessage);
}

function showOpProgressOnId(id, message, preMessage) {
    showOpOnId(id, "progress-status", message, preMessage);
}

function showOpSuccessOnId(id, message, preMessage) {
    showOpOnId(id, "success-status", message, preMessage);
}

function showOpErrorOnId(id, message, preMessage) {
    showOpOnId(id, "error-status", message, preMessage);
}

function showOpOnId(id, msgLevelClass, message, preMessage) {
    hideServerOpOnId(id);
    let idSelector = cleanupIdHtmlSelector(id);
    $("#" + idSelector + " ." + msgLevelClass + " .alert .message").text(message);
    let preElement = $("#" + idSelector + " ." + msgLevelClass + " .alert .pre-message");
    if (preMessage) {
        preElement.text(preMessage);
        preElement.css("display", "block");
    } else {
        preElement.css("display", "none");
    }
    $("#" + idSelector + " ." + msgLevelClass).css("display", "block");
}

function hideServerOpOnId(id) {
    $("#" + cleanupIdHtmlSelector(id) + " .info").each(function () {
        $(this).css("display", "none");
    });
}

function cleanupIdHtmlSelector(id) {
    return id.replace(/\./g, "\\.");
}