$(document).ready(function () {
    $("#refresh-btn").click(refreshCluster);
    $(".status-filter-btn").click(function (){
        let statusType = $(this).attr("data-status-type");
        filterDatatableBy(statusType);
    });
    maybeFilterDatatableByUrlHash();
});

function refreshCluster() {
    console.log("Going to refresh cluster state");
    let clusterIdentifier = $("meta[name=cluster-identifier]").attr("content");
    $.post("api/clusters/refresh/cluster", {clusterIdentifier: clusterIdentifier})
        .done(function () {
            console.log("Success on refresh clusters state");
            location.reload();
        })
        .fail(function (error) {
            console.log("Failed refresh clusters state");
            console.log(error);
        });
}

