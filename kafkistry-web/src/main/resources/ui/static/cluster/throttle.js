$(document).ready(function () {
    $(".apply-throttle-btn").click(applyThrottle);
});

function applyThrottle() {
    let button = $(this);
    let brokerId = button.attr("data-brokerId");
    let row = button.closest(".throttle-row");
    let throttle = {
        leaderRate: parseRate(row.find("input[name='leader.rate']").val()),
        followerRate: parseRate(row.find("input[name='follower.rate']").val()),
        alterDirIoRate: parseRate(row.find("input[name='alterDirIo.rate']").val())
    };
    let clusterIdentifier = $("meta[name=cluster-identifier]").attr("content");
    if (brokerId === "ALL") {
        doApplyThrottle(clusterIdentifier, throttle, "api/clusters-management/throttle", "broker-ALL");
    } else {
        doApplyThrottle(clusterIdentifier, throttle, "api/clusters-management/throttle/" + brokerId, "broker-" + brokerId);
    }
}

function doApplyThrottle(clusterIdentifier, throttle, urlPath, opStatusId) {
    showOpProgressOnId(opStatusId, "Applying throttle...");
    $
        .ajax(urlPath + "?clusterIdentifier=" + encodeURI(clusterIdentifier), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(throttle)
        })
        .done(function () {
            showOpSuccessOnId(opStatusId, "Successfully applied throttle");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpErrorOnId(opStatusId, "Failed to apply throttle:", errorMsg);
        });

}

function parseRate(value) {
    let rate = parseInt(value);
    if (isNaN(rate) || rate <= 0) {
        return null;
    }
    return rate;
}

