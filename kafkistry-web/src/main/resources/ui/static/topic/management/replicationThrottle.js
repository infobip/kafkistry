$(document).ready(initThrottleETA);

function initThrottleETA() {
    $("#throttlePerSec-input").on("change, keypress, click, input", null, null, refreshReAssignETA);
    $("#throttleUnit-input").change(refreshReAssignETA);
    refreshReAssignETA();
}

function getThrottleBytesPerSec() {
    let throttlePerSec = parseInt($("#throttlePerSec-input").val());
    let throttleUnit = $("#throttleUnit-input").val();
    switch (throttleUnit) {
        case "MB/sec":
            return throttlePerSec * 1024 * 1024;
        case "kB/sec":
            return throttlePerSec * 1024;
        default:
            return throttlePerSec;
    }
}

function refreshReAssignETA() {
    let etaElement = $("#reassign-eta");
    let bytes = parseInt(etaElement.attr("data-max-io-bytes"));
    let throttleBytesPerSec = getThrottleBytesPerSec();
    let etaMs = round1d(1000 * bytes / throttleBytesPerSec);
    let duration = prettyDuration(etaMs)
    etaElement.text(duration);
    $("#reassign-eta-explain").text("(biggest per broker IO ~ "+prettyBytesValue(bytes)+")");
}