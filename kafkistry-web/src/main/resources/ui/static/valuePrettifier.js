$(document).ready(function () {
    refreshAllConfValues();
    refreshAllConfInputValues();
    $(document).on("input, change, keypress, keyup", "input.conf-value-in", null, refreshInputConfigValue);
});

function refreshInputConfigValue(preventRefreshYaml) {
    let input = $(this);
    let name = input.attr("name");
    let value = input.val();
    let expandedVal = expandValue(name, value);
    if (expandedVal) {
        value = expandedVal.toString();
        input.val(value);
        if (typeof refreshYaml !== "undefined") {
            if (preventRefreshYaml === undefined || (preventRefreshYaml instanceof jQuery.Event)) refreshYaml();
        }
        let callback = input.attr("data-callback");
        if (callback) {
            window[callback]();
        }
    }
    let prettyVal = prettyValue(name, value, input.attr("data-nan-default"));
    let parent = input.closest(".conf-value");
    if (parent.length === 0) {
        parent = input.closest("label");
    }
    let element = parent.find(".conf-value-out");
    if (prettyVal !== null) {
        element.text("(" + prettyVal + ")");
    } else {
        element.text("");
    }
    setupTooltipHelp(input, name, parent);
}

function refreshAllConfInputValues() {
    $("input.conf-value-in").each(function () {
        let inTemplate = $(this).closest(".template").length > 0;
        if (!inTemplate) {
            refreshInputConfigValue.call(this);
        }
    });
}

function refreshAllConfValues() {
    refreshAllConfValuesIn($(document));
}

function refreshAllConfValuesIn(container) {
    container.find(".conf-value").each(function () {
        let element = $(this);
        let inTemplate = element.closest(".template").length > 0;
        if (!inTemplate) {
            let name = element.attr("data-name");
            let value = element.attr("data-value");
            let prettyVal = prettyValue(name, value);
            if (prettyVal !== null) {
                if (element.hasClass("conf-in-message")) {
                    element.html("<span class='text-nowrap'>" + prettyVal + "</span>");
                } else {
                    element.append(" <span class='small text-primary text-nowrap'>(" + prettyVal + ")</span>");
                }
            }
        }
    })
}

function prettyValue(name, value, nanDefault) {
    if (value === undefined) {
        return null;
    }
    let valueNum = value.indexOf(".") > -1 ? parseFloat(value) : parseInt(value);
    if (isNaN(valueNum)) {
        if (nanDefault) {
            return nanDefault;
        }
        return null;
    }
    if (valueNum === Math.pow(2, 31) - 1) {
        return "Integer.MAX";
    }
    if (valueNum === Math.pow(2, 63) - 1) {
        return "Long.MAX";
    }
    if (isMillis(name)) {
        if (valueNum >= 0) {
            return prettyMillisValue(valueNum);
        }
    }
    if (isSeconds(name)) {
        if (valueNum >= 0) {
            return prettyMillisValue(valueNum * 1000);
        }
    }
    if (isBytes(name)) {
        if (valueNum >= 0) {
            return prettyBytesValue(valueNum);
        }
    }
    if (isMsgRate(name)) {
        if (valueNum >= 0) {
            return prettyNumber(valueNum) + " msg/sec";
        }
    }
    if (isRate(name)) {
        if (valueNum >= 0) {
            return prettyBytesValue(valueNum) + "/sec";
        }
    }
    if (isPercent(name)) {
        if (valueNum >= 0) {
            return prettyPercentValue(valueNum);
        }
    }
    return null;
}

function isMillis(name) {
    return name.endsWith(".ms");
}

function isSeconds(name) {
    return name.endsWith(".seconds") || name.endsWith(".secs");
}

function isBytes(name) {
    return name.endsWith(".bytes") || name.endsWith(".buffer.size");
}

function isRate(name) {
    return name.endsWith(".rate") || name.endsWith(".bytes.per.second") || name.endsWith("ByteRate");
}

function isMsgRate(name) {
    return name.endsWith(".msg.rate");
}

function isPercent(name) {
    return name.endsWith(".percent");
}

function setupTooltipHelp(input, name, parent) {
    let helpText = null;
    if (isMillis(name)) {
        helpText = "Enter number of <strong>milliseconds</strong>.<br/>" +
            "Type one of following conversion shortcuts [" +
            "<code>s</code>, " +
            "<code>m</code>, " +
            "<code>h</code>, " +
            "<code>d</code>, " +
            "<code>w</code>] " +
            "after number.<br/>" +
            "Input conversion examples:" +
            "<ul>" +
            "   <li><code>4s</code> - seconds</li>" +
            "   <li><code>10m</code> - minutes</li>" +
            "   <li><code>12h</code> - hours</li>" +
            "   <li><code>3d</code> - days</li>" +
            "   <li><code>2w</code> - weeks</li>" +
            "</ul>";
    } else if (isBytes(name) || isRate(name)) {
        let suffix = isRate(name) ? "/sec" : "";
        helpText = "Enter number of <strong>bytes" + suffix + "</strong>.<br/>" +
            "Type one of following conversion shortcuts [" +
            "<code>k</code>, " +
            "<code>m</code>, " +
            "<code>g</code>, " +
            "<code>t</code>] " +
            "after number." +
            "Input conversion examples:" +
            "<ul>" +
            "   <li><code>8k</code> - kilobytes" + suffix + "</li>" +
            "   <li><code>30m</code> - megabytes" + suffix + "</li>" +
            "   <li><code>2g</code> - gigabytes" + suffix + "</li>" +
            "   <li><code>1t</code> - terabytes" + suffix + "</li>" +
            "</ul>";
    }
    if (!helpText) {
        return;
    }
    let help = parent.find(".conf-help");
    if (help.length === 0) {
        parent.append('<span class="conf-help info-icon circle">?</span>');
        help = parent.find(".conf-help");
    }
    if (help.attr("tooltip-initialized") === "true") {
        return;
    }
    help.addClass("info-icon");
    help.attr("title", helpText);
    help.attr("data-placement", "bottom");
    help.attr("data-toggle", "tooltip");
    help.attr("data-html", "true");
    help.attr("tooltip-initialized", "true");
    help.tooltip();
}

function prettyMillisValue(millis) {
    if (millis < 1000) {
        return millis + " ms";
    }
    let sec = millis / 1000;
    if (sec < 60) {
        return round1d(sec) + " sec"
    }
    let min = sec / 60;
    if (min < 60) {
        return round1d(min) + " min"
    }
    let hrs = min / 60;
    if (hrs < 24) {
        return round1d(hrs) + " h"
    }
    let days = hrs / 24;
    if (days < 30) {
        return round1d(days) + " day"
    }
    let months = days / 30;
    if (months < 12) {
        return round1d(months) + " month"
    }
    let years = months / 12;
    return round1d(years) + " year"
}

function prettyBytesValue(bytes) {
    if (bytes < 1024) {
        return bytes + "B";
    }
    let kb = bytes / 1024;
    if (kb < 1024) {
        return round1d(kb) + "KB";
    }
    let mb = kb / 1024;
    if (mb < 1024) {
        return round1d(mb) + "MB";
    }
    let gb = mb / 1024;
    if (gb < 1024) {
        return round1d(gb) + "GB";
    }
    let tb = gb / 1024;
    if (tb < 1024) {
        return round1d(tb) + "TB";
    }
    let yb = tb / 1024;
    return round1d(yb) + "YB"
}

function prettyPercentValue(percent) {
    if (percent < 0) {
        return "-" + prettyPercentValue(-percent);
    }
    return prettyNumber(percent) + "%"
}

function prettyNumber(number) {
    if (number === 0) {
        return "0";
    }
    if (Math.abs(number) < 1) {
        let log = Math.round(Math.log10(Math.abs(number)));
        let factor = Math.pow(10, -log + 2);
        let rounded = Math.round(number * factor) / factor;
        return rounded.toString();
    }
    if (Math.abs(number) < 1000) {
        return round1d(number).toString();
    }
    let numK = number / 1000;
    if (Math.abs(numK) < 1000) {
        return round1d(numK) + "k";
    }
    let numM = numK / 1000;
    if (Math.abs(numM) < 1000) {
        return round1d(numM) + "M";
    }
    let numG = numM / 1000;
    if (Math.abs(numG) < 1000) {
        return round1d(numG) + "G";
    }
    let numT = numG / 1000;
    if (Math.abs(numT) < 1000) {
        return round1d(numT) + "T";
    }
    let numP = numT / 1000;
    return round1d(numP) + "P";
}

function round1d(number) {
    let result = Math.round(number * 10) / 10;
    let diff = Math.abs(result - number);
    if (diff > 0 && result.toString().indexOf(".") === -1) {
        return result + ".0";
    }
    return result;
}

let VALUE_SIZE_UNIT_REGEX = /([\d.]+)\s*([kmgt])/i;
let VALUE_TIME_UNIT_REGEX = /([\d.]+)\s*([smhdw])/i;

function expandValue(name, value) {
    if (name.endsWith(".ms")) {
        return expandTimeValue(value);
    }
    if (name.endsWith(".bytes") || name.endsWith(".rate") || name.endsWith("ByteRate")) {
        return expandSizeValue(value);
    }
    return null;
}

function expandSizeValue(value) {
    let match = value.match(VALUE_SIZE_UNIT_REGEX);
    if (match === null) {
        return null;
    }
    let amount = parseFloat(match[1]);
    let factor = 1;
    switch (match[2].toLowerCase()) {
        case "k":
            factor = 1024;
            break;
        case "m":
            factor = 1024 * 1024;
            break;
        case "g":
            factor = 1024 * 1024 * 1024;
            break;
        case "t":
            factor = 1024 * 1024 * 1024 * 1024;
            break;
    }
    return Math.round(amount * factor);

}

function expandTimeValue(value) {
    let match = value.match(VALUE_TIME_UNIT_REGEX);
    if (match === null) {
        return null;
    }
    let amount = parseFloat(match[1]);
    let factor = 1;
    switch (match[2].toLowerCase()) {
        case "s":
            factor = 1000;
            break;
        case "m":
            factor = 1000 * 60;
            break;
        case "h":
            factor = 1000 * 60 * 60;
            break;
        case "d":
            factor = 1000 * 60 * 60 * 24;
            break;
        case "w":
            factor = 1000 * 60 * 60 * 24 * 7;
            break;
    }
    return Math.round(amount * factor);
}
