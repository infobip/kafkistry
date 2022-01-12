let existingConsumerGroups = [];

$(document).ready(function () {
    existingConsumerGroups = $(".existing-consumer-group").map(function () {
        return $(this).attr("data-consumer-group-id");
    }).get();

    let otherConsumerGroupInput = $("input[name=otherConsumerGroup]");
    otherConsumerGroupInput.on("change keyup", updateCloneGroupButtonUrl);
    otherConsumerGroupInput
        .autocomplete({
            source: existingConsumerGroups,
            select: function () {
                setTimeout(updateCloneGroupButtonUrl, 20);
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });

    $("#clone-acton-btn").click(function () {
        $("#clone-form").show();
    });
    $("#cancel-clone-btn").click(function () {
        $("#clone-form").hide();
    });
    $("#clone-from-btn").click(checkOtherGroupUrl);
    $("#clone-into-btn").click(checkOtherGroupUrl);
    whenUrlSchemaReady(updateCloneGroupButtonUrl);
});

function checkOtherGroupUrl() {
    let otherConsumerGroup = $("input[name=otherConsumerGroup]").val();
    if (!otherConsumerGroup) {
        return false;   //prevent empty into consumerGroup
    }
}

function updateCloneGroupButtonUrl() {
    let clusterIdentifier = $("meta[name=clusterIdentifier]").attr("content");
    let consumerGroup = $("meta[name=consumerGroupId]").attr("content");
    let otherConsumerGroup = $("input[name=otherConsumerGroup]").val();

    let urlCloneInto = urlFor("consumerGroups.showCloneConsumerGroup", {
        clusterIdentifier: clusterIdentifier,
        fromConsumerGroupId: consumerGroup,
        intoConsumerGroupId: otherConsumerGroup,
    });
    let urlCloneFrom = urlFor("consumerGroups.showCloneConsumerGroup", {
        clusterIdentifier: clusterIdentifier,
        fromConsumerGroupId: otherConsumerGroup,
        intoConsumerGroupId: consumerGroup,
    });
    let buttonsEnabled = !!otherConsumerGroup;
    let otherIsExisting = existingConsumerGroups.includes(otherConsumerGroup);

    let intoBtn = $("#clone-into-btn");
    intoBtn.attr("href", urlCloneInto);
    intoBtn.attr("title", "Clone offsets of '" + consumerGroup + "' -> '" + otherConsumerGroup + "'");
    if (buttonsEnabled) {
        intoBtn.removeClass("disabled");
    } else {
        intoBtn.addClass("disabled");
    }

    let fromBtn = $("#clone-from-btn");
    fromBtn.attr("href", urlCloneFrom);
    fromBtn.attr("title", "Clone offsets of '" + otherConsumerGroup + "' -> '" + consumerGroup + "'");
    if (buttonsEnabled && otherIsExisting) {
        fromBtn.removeClass("disabled");
    } else {
        fromBtn.addClass("disabled");
    }
}
