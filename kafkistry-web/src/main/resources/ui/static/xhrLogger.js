$(document).ajaxComplete(function (e, xhr, options) {
    let statusCode = xhr.status;
    let endpoint = options.url;
    let serverHostname = xhr.getResponseHeader("Server-Hostname");
    console.log("XHR completed: httpStatus=" + statusCode + " servedBy=" + serverHostname + " endpoint=" + endpoint);
});
