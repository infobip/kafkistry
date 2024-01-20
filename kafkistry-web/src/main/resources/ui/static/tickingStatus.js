let tickingId = null;
let tickingSeconds;

function startTicking(seconds, opStatusId, message) {
    stopTicking();
    tickingSeconds = seconds;
    let showTimeoutStatus = function () {
        let preMessage = tickingSeconds >= 0
            ? "Timeout: " + tickingSeconds + " / " + seconds + " sec"
            : "Timed-out before: " + (-tickingSeconds) + "sec, timeout was " + seconds + " sec";
        showOpProgressOnId(opStatusId, message, preMessage);
    }
    showTimeoutStatus();
    tickingId = setInterval(function () {
        tickingSeconds--;
        showTimeoutStatus();
    }, 1000);
}

function stopTicking() {
    if (tickingId) {
        clearInterval(tickingId);
    }
}
