$(document).ready(formatTimestamp);

function formatTimestamp() {
    formatTimestampIn($(document));
}

function formatTimestampIn(container) {
    container.find(".time").each(function () {
        let element = $(this);
        let timestamp = parseInt(element.attr("data-time"));
        let prettyTime = new Date(timestamp).toLocaleString();
        let beforeMillis = Date.now() - timestamp;
        let pretty;
        if (beforeMillis < 0) {
            pretty = "in "+ prettyDuration(-beforeMillis);
        } else {
            pretty = prettyDuration(beforeMillis) + " ago"
        }
        element.html(prettyTime + " (" + pretty.replace(" ", "&nbsp;") + ")");
    });
    container.find("span[data-time-sec]").each(function () {
        let element = $(this);
        let timestampSec = parseInt(element.attr("data-time-sec"));
        let prettyTime = new Date(timestampSec * 1000).toLocaleString();
        let username = element.attr("data-username");
        element.attr("title", username + " @ " + prettyTime)
    });
}

function prettyDuration(durationMs) {
    if (durationMs < 1000) {
        return durationMs + " ms";
    }
    let durationSec = Math.round(durationMs / 1000);
    if (durationSec < 60) {
        return durationSec + " sec";
    }
    let durationMin = Math.round(durationSec / 60);
    if (durationMin < 60) {
        return durationMin + " min";
    }
    let durationHours = Math.round(durationMin / 60);
    if (durationHours < 24) {
        return durationHours + " hours";
    }
    let durationDays = Math.round(durationHours / 24);
    if (durationDays < 30) {
        return durationDays + " days";
    }
    let durationMonths = Math.round(durationDays / 30);
    if (durationMonths < 12) {
        return durationMonths + " months";
    }
    let durationYears = Math.round(durationMonths / 12);
    return durationYears + " years";
}