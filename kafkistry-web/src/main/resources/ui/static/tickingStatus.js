let tickingId = null;
let tickingSeconds;

function startTicking(seconds, opStatusId, message) {
    stopTicking();
    tickingSeconds = seconds;
    tickingId = setInterval(function () {
        showOpProgressOnId(opStatusId, message, "Timeout: " + tickingSeconds + " / " + seconds + " sec");
        tickingSeconds--;
    }, 1000);
}

function stopTicking() {
    if (tickingId) {
        clearInterval(tickingId);
    }
}
