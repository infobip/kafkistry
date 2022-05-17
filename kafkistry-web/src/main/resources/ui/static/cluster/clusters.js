$(document).ready(function () {
    $("#refresh-btn").click(refreshClusters);
    whenDatatableInitialized("clusters", loadClustersBriefInspections);
    maybeFilterDatatableByUrlHash();
});

function refreshClusters() {
    console.log("Going to refresh clusters state");
    $.post("api/clusters/refresh")
        .done(function () {
            console.log("Success on refresh clusters state");
            location.reload();
        })
        .fail(function (error) {
            console.log("Failed refresh clusters state");
            console.log(error);
        });
}

function loadClustersBriefInspections() {
    $("#clusters").DataTable().rows().every(function () {
        let row = this;
        let rowNode = $(row.nodes()[0]);
        let clusterIdentifier = rowNode.attr("data-clusterIdentifier");
        console.log("loading status for "+clusterIdentifier);

        let isDisabled = rowNode.attr("data-clusterState") === "DISABLED";
        let resultContainer =  rowNode.find("#cluster-brief-inspect-result_" + clusterIdentifier);
        if (isDisabled) {
            resultContainer.html("<span><i>---</i></span>");
        } else {
            loadBriefInspect(clusterIdentifier, resultContainer, row, rowNode);
        }
    });
}

function loadBriefInspect(clusterIdentifier, resultContainer, row, rowNode) {
    let opProgressId = "clusterBriefInspect_" + clusterIdentifier;
    showOpProgressOnIdIn(opProgressId, "Loading inspect...", undefined, rowNode);
    whenUrlSchemaReady(function () {
        let url = urlFor("clusters.showClusterInspectBrief", {clusterIdentifier: clusterIdentifier});
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnIdIn(opProgressId, rowNode);
                resultContainer.html(response);
                registerAllInfoTooltipsIn(resultContainer);
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnIdIn(opProgressId, rowNode);
                    let breakableErrHtml = errHtml.replace(/\./g, ".​");    //lengthy exception cass names
                    resultContainer.html(breakableErrHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnIdIn(opProgressId, "Failed to get cluster inspect", errorMsg, rowNode);
                }
            })
            .always(function () {
                contentUpdated(row);
            });
    });
}

function contentUpdated(row) {
    row.invalidate();   //content is updated, make it available to search
    row.draw();         //re-search existing term
}
