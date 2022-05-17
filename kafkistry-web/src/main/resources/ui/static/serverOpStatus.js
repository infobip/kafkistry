function hideOpStatus() {
    hideOpStatusIn($(document));
}

function hideOpStatusIn(container) {
    hideServerOpOnIdIn("server-op-status", container);
}

function showOpProgress(message, preMessage) {
    showOpProgressIn(message, preMessage, $(document));
}

function showOpProgressIn(message, preMessage, container) {
    showOpProgressOnIdIn("server-op-status", message, preMessage, container);
}

function showOpSuccess(message, preMessage) {
    showOpSuccessIn(message, preMessage, $(document));
}

function showOpSuccessIn(message, preMessage, container) {
    showOpSuccessOnIdIn("server-op-status", message, preMessage, container);
}

function showOpError(message, preMessage) {
    showOpErrorIn(message, preMessage, $(document));
}

function showOpErrorIn(message, preMessage, container) {
    showOpErrorOnIdIn("server-op-status", message, preMessage, container);
}

function showOpProgressOnId(id, message, preMessage) {
    showOpProgressOnIdIn(id, message, preMessage, $(document));
}

function showOpProgressOnIdIn(id, message, preMessage, container) {
    showOpOnIdIn(id, "progress-status", message, preMessage, container);
}

function showOpSuccessOnId(id, message, preMessage) {
    showOpSuccessOnIdIn(id, message, preMessage, $(document));
}

function showOpSuccessOnIdIn(id, message, preMessage, container) {
    showOpOnIdIn(id, "success-status", message, preMessage, container);
}

function showOpErrorOnId(id, message, preMessage) {
    showOpErrorOnIdIn(id, message, preMessage, $(document));
}

function showOpErrorOnIdIn(id, message, preMessage, container) {
    showOpOnIdIn(id, "error-status", message, preMessage, container);
}

function showOpOnId(id, msgLevelClass, message, preMessage) {
    showOpOnIdIn(id, msgLevelClass, message, preMessage, $(document));
}

function showOpOnIdIn(id, msgLevelClass, message, preMessage, container) {
    hideServerOpOnIdIn(id, container);
    let idSelector = cleanupIdHtmlSelector(id);
    container.find("#" + idSelector + " ." + msgLevelClass + " .alert .message").text(message);
    let preElement = container.find("#" + idSelector + " ." + msgLevelClass + " .alert .pre-message");
    if (preMessage) {
        preElement.text(preMessage);
        preElement.css("display", "block");
    } else {
        preElement.css("display", "none");
    }
    container.find("#" + idSelector + " ." + msgLevelClass).css("display", "block");
}

function hideServerOpOnId(id) {
    hideServerOpOnIdIn(id, $(document));
}

function hideServerOpOnIdIn(id, container) {
    container.find("#" + cleanupIdHtmlSelector(id) + " .info").each(function () {
        $(this).css("display", "none");
    });
}

function cleanupIdHtmlSelector(id) {
    return id.replace(/\./g, "\\.");
}