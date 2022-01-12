$(document).ready(function () {
    $("#refresh-btn").click(refreshClusters);
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
