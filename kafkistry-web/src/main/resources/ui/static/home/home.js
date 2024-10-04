$(document).ready(function () {
    whenUrlSchemaReady(function () {
        fetchStats("main.showPendingRequests", [
            {statusId: "pending-requests", containerSelector: "#pending-requests-container"},
        ]);
        fetchStats("main.showClustersStats", [
            {statusId: "clusters-stats", containerSelector: "#clusters-stats-container"},
        ]);
        fetchStats("main.showTagsStats", [
            {statusId: "tags-stats", containerSelector: "#tags-stats-container"},
        ]);
        fetchStats("main.showTopicsStats", [
            {statusId: "topics-stats", containerSelector: "#topics-stats-container", responseSelector: ".all-topics-stats"},
            {statusId: "topics-your-stats", containerSelector: "#topics-your-stats-container", responseSelector: ".owned-topics-stats"},
        ]);
        fetchStats("main.showConsumerGroupsStats", [
            {statusId: "consumer-groups-stats", containerSelector: "#consumer-groups-stats-container", responseSelector: ".all-consumers-stats"},
            {statusId: "consumer-groups-your-stats", containerSelector: "#consumer-groups-your-stats-container", responseSelector: ".owned-consumers-stats"},
        ]);
        fetchStats("main.showAclsStats", [
            {statusId: "acls-stats", containerSelector: "#acls-stats-container", responseSelector: ".all-acls-stats"},
            {statusId: "acls-your-stats", containerSelector: "#acls-your-stats-container", responseSelector: ".owned-acls-stats"},
        ]);
        fetchStats("main.showQuotasStats", [
            {statusId: "quotas-stats", containerSelector: "#quotas-stats-container"},
        ]);
    });
});

/**
 * @param {String} urlTarget
 * @param {{statusId: String, containerSelector: String, responseSelector: String?}[]} components
 */
function fetchStats(urlTarget, components) {
    components.forEach((component) => {
        showOpProgressOnId(component.statusId, "Loading...");
    });
    $
        .ajax(urlFor(urlTarget), {
            method: "GET",
            headers: {ajax: 'true'},
        })
        .done(function (response) {
            components.forEach((component) => {
                hideServerOpOnId(component.statusId);
                let resultHtml;
                if (component.responseSelector) {
                    resultHtml = $("<div>").html(response).find(component.responseSelector).html();
                } else {
                    resultHtml = response;
                }
                $(component.containerSelector).html(resultHtml);
            });
            registerAllInfoTooltips();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                components.forEach((component) => {
                    hideServerOpOnId(component.statusId);
                    $(component.containerSelector).html(errHtml);
                });
            } else {
                let errorMsg = extractErrMsg(error);
                components.forEach((component) => {
                    showOpErrorOnId(component.statusId, "Failed to fetch stats", errorMsg);
                });
            }
        });
}
