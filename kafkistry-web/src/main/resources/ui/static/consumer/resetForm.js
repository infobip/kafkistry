$(document).ready(function () {
    $("input[name=selectAll]").change(selectAllChanged);
    $("input[name=topic]").change(topicCheckboxChanged);
    $("input[name=partition]").change(partitionCheckboxChanged);
});

function selectAllChanged() {
    let checked = $(this).is(":checked");
    $("input[name=topic], input[name=partition]").prop('checked', checked);
    if (checked) {
        $(".selectAll").hide();
        $(".deselectAll").show();
    } else {
        $(".selectAll").show();
        $(".deselectAll").hide();
    }
}

function topicCheckboxChanged() {
    let check = $(this);
    let checked = check.is(":checked");
    check.closest(".topic").find("input[name=partition]").prop('checked', checked);
}

function partitionCheckboxChanged() {
    let check = $(this);
    let checked = check.is(":checked");
    let topicCheck = check.closest(".topic").find("input[name=topic]");
    if (checked) {
        topicCheck.prop('checked', true);
    } else {
        let partitions = check.closest(".topic").find("input[name=partition]").map(function () {
            return $(this).is(":checked")
        }).get();
        let allUnchecked = partitions.every(function (checked) {
            return !checked
        });
        if (allUnchecked) {
            topicCheck.prop('checked', false);
        }
    }
}

function extractTopicPartitions() {
    return $(".topic").map(function () {
        let topicCheck = $(this).find("input[name=topic]");
        if (topicCheck.is(":checked")) {
            let topic = topicCheck.attr("data-topic");
            let partitions = $(this).find("input[name=partition]").map(function () {
                if ($(this).is(":checked")) {
                    return {
                        partition: parseInt($(this).attr("data-partition"))
                    };
                } else {
                    return null;
                }
            }).get().filter(function (partition) {
                return partition != null;
            });
            return {
                topic: topic,
                partitions: partitions
            };
        } else {
            return null;
        }
    }).get().filter(function (topic) {
        return topic != null;
    });
}

function formatOffsetsChange(change) {
    let result = "Consumer group: " + change.groupId + "\n";
    if (change.totalRewind > 0) {
        result += "Total rewind backwards: " + change.totalRewind + "\n";
    }
    if (change.totalSkip > 0) {
        result += "Total skip forward: " + change.totalSkip + "\n";
    }
    if (change.totalLag > 0) {
        result += "Total lag on all " + change.changes.length + " affected partitions: " + change.totalLag + "\n";
    } else if (change.changes.length) {
        result += "No lag on all " + change.changes.length + " affected partitions\n";
    }
    if (!(change.totalRewind > 0 || change.totalSkip > 0)) {
        result += "No offset seeks\n";
    }
    change.changes.forEach(function (change) {
        let delta = change.delta === null ? "(none)" : change.delta;
        result += "    " + change.topic + " : " + change.partition + " : new-offset = " + change.offset + " : delta = " + delta + " : lag = " + change.lag + "\n";
    });
    return result;
}




