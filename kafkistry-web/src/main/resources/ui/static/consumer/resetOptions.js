$(document).ready(function () {
    $("select[name=resetType]").change(adjustResetOptionVisibility);
    $("input[type=number]").change(adjustResetOptionVisibility).keydown(function () {
        setTimeout(adjustResetOptionVisibility, 10);
    });
    adjustResetOptionVisibility();
    initDatePicker();
});

function adjustResetOptionVisibility() {
    let resetType = $("select[name=resetType]").val();
    $(".reset-option").hide();
    let offsetSeek = extractOffsetSeek();
    $(".type").removeClass("legend-highlight");
    switch (resetType) {
        case "EARLIEST":
            $(".begin-option").show();
            if (offsetSeek.offset > 0) {
                $(".type-begin-n").addClass("legend-highlight");
            } else {
                $(".type-begin").addClass("legend-highlight");
            }
            break;
        case "LATEST":
            $(".end-option").show();
            if (offsetSeek.offset > 0) {
                $(".type-end-n").addClass("legend-highlight");
            } else {
                $(".type-end").addClass("legend-highlight");
            }
            break;
        case "RELATIVE":
            $(".relative-option").show();
            if (offsetSeek.offset > 0) {
                $(".type-current-pn").addClass("legend-highlight");
            } else if (offsetSeek.offset < 0) {
                $(".type-current-mn").addClass("legend-highlight");
            } else {
                $(".type-current").addClass("legend-highlight");
            }
            break;
        case "TIMESTAMP":
            $(".timestamp-option").show();
            $(".type-timestamp").addClass("legend-highlight");
            break;
        case "EXPLICIT":
            $(".explicit-option").show();
            $(".type-explicit").addClass("legend-highlight");
            break;
    }
}

function initDatePicker() {
    $.datetimepicker.setDateFormatter("moment");
    let minutesStep = 10;
    let timestampInput = $("input[name=timestamp]");
    timestampInput.val(Date.now().toString());
    let timestampPicked = function (currTime, picker) {
        let currTimestampMillis = currTime.getTime();
        let roundedTimeMillis = currTimestampMillis - (currTimestampMillis % (1000 * 60 * minutesStep));
        picker.val(roundedTimeMillis.toString());
    };
    timestampInput.datetimepicker({
        format: 'x',
        step: minutesStep,
        onSelectTime: timestampPicked,
        onSelectDate: timestampPicked,
    });
    timestampInput.change(updatePickedTime);
    updatePickedTime();
}

function updatePickedTime() {
    let timestampInput = $("input[name=timestamp]");
    $("#picked-time-indicator").attr("data-time", timestampInput.val());
    formatTimestampIn($(".timestamp-option"));
}

function extractOffsetSeek() {
    let resetType = $("select[name=resetType]").val();
    let offsetStr = "";
    let timestampStr = "";
    switch (resetType) {
        case "EARLIEST":
            offsetStr = $("input[name=begin-offset]").val();
            break;
        case "LATEST":
            offsetStr = $("input[name=end-offset]").val();
            break;
        case "RELATIVE":
            offsetStr = $("input[name=relative-offset]").val();
            break;
        case "TIMESTAMP":
            timestampStr = $("input[name=timestamp]").val();
            break;
        case "EXPLICIT":
            offsetStr = $("input[name=explicit-offset]").val();
            break;
    }
    return {
        type: resetType,
        offset: offsetStr ? parseInt(offsetStr) : null,
        timestamp: timestampStr ? parseInt(timestampStr) : null,
    };
}
