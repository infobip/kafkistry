$(document).ready(function () {
    $.fn.selectpicker.Constructor.DEFAULTS.whiteList.span.push('title');
    $("#add-messages-rate-override-btn").click(addMessagesRateOverride);
    $(".messagesRateOverrides").on("click", ".remove-messages-rate-override-btn", null, removeMessagesRateOverride);
    initSelectLocationPickers(".requirements-message-rate");
    $("#add-retention-override-btn").click(addDataRetentionOverride);
    $(".dataRetentionOverrides").on("click", ".remove-data-retention-override-btn", null, removeDataRetentionOverride);
    initSelectLocationPickers(".requirements-data-retention");
});

function tryParseInt(string) {
    let result = parseInt(string);
    if (isNaN(result)) {
        return undefined;
    }
    return result;
}

function extractResourceRequirements() {
    let wrapper = $(".resource-requirements-input");
    if (wrapper.length) {
        if (wrapper.hasClass("disabled")) {
            return null;
        }
    }
    let messagesRateAmount = $(".messagesRateDefault input[name='messagesRateAmount']").val();
    let messagesRateFactor = $(".messagesRateDefault select[name='messagesRateFactor']").val();
    let messagesRateUnit = $(".messagesRateDefault select[name='messagesRateUnit']").val();
    let avgMessageSize = $("input[name='avgMessageSize']").val();
    let avgMessageSizeUnit = $("select[name='avgMessageSizeUnit']").val();
    let retentionAmount =  $(".dataRetentionDefault [name='retentionAmount']").val();
    let retentionUnit =  $(".dataRetentionDefault select[name='retentionUnit']").val();
    let messagesRateOverrides = {};
    let messagesRateTagOverrides = {};
    $(".messagesRateOverrides .messages-rate-override").each(function (){
        let override = $(this);
        let selectedLocation = selectedLocationIn(override);
        let amount = override.find("input[name='messagesRateAmount']").val();
        let factor = override.find("select[name='messagesRateFactor']").val();
        let unit = override.find("select[name='messagesRateUnit']").val();
        let rate = {
            amount: tryParseInt(amount),
            factor: factor,
            unit: unit
        };
        switch (selectedLocation.type) {
            case "TAG":
                messagesRateTagOverrides[selectedLocation.value] = rate;
                break;
            case "CLUSTER":
                messagesRateOverrides[selectedLocation.value] = rate;
                break;
        }
    });
    let retentionOverrides = {};
    let retentionTagOverrides = {};
    $(".dataRetentionOverrides .data-retention-override").each(function (){
        let override = $(this);
        let selectedLocation = selectedLocationIn(override);
        let amount = override.find("input[name='retentionAmount']").val();
        let unit = override.find("select[name='retentionUnit']").val();
        let rate = {
            amount: tryParseInt(amount),
            unit: unit
        };
        switch (selectedLocation.type) {
            case "TAG":
                retentionTagOverrides[selectedLocation.value] = rate;
                break;
            case "CLUSTER":
                retentionOverrides[selectedLocation.value] = rate;
                break;
        }
    });
    return {
        avgMessageSize: {
            amount: tryParseInt(avgMessageSize),
            unit: avgMessageSizeUnit
        },
        retention: {
            amount: tryParseInt(retentionAmount),
            unit: retentionUnit
        },
        retentionOverrides: retentionOverrides,
        retentionTagOverrides: retentionTagOverrides,
        messagesRate: {
            amount: tryParseInt(messagesRateAmount),
            factor: messagesRateFactor,
            unit: messagesRateUnit
        },
        messagesRateOverrides: messagesRateOverrides,
        messagesRateTagOverrides: messagesRateTagOverrides,
    };
}

function validateResourceRequirements(resourceRequirements) {
    let errors = [];
    if (!(resourceRequirements.retention.amount > 0)) {
        errors.push("Requirement for retention amount must be positive number");
    }
    if (!(resourceRequirements.avgMessageSize.amount > 0)) {
        errors.push("Requirement for avg msg size must be positive number");
    }
    if (!(resourceRequirements.messagesRate.amount > 0)) {
        errors.push("Requirement for msg rate must be positive number");
    }
    function validateOverrides(overrides, forWhat) {
        Object.keys(overrides).forEach(function (key) {
            let override = overrides[key];
            if (!(override.amount > 0)) {
                errors.push("Requirement for " + forWhat + " of '" + key + "' must be positive number");
            }
        });
    }
    validateOverrides(resourceRequirements.messagesRateOverrides, "message rate cluster override");
    validateOverrides(resourceRequirements.messagesRateTagOverrides, "message rate tag override");
    validateOverrides(resourceRequirements.retentionOverrides, "retention cluster override");
    validateOverrides(resourceRequirements.retentionTagOverrides, "retention tag override");
    return errors;
}

function toggleResourceRequirementsDefined() {
    let checkbox = $(this);
    let defined = checkbox.is(":checked");
    if (defined) {
        $(".resource-requirements-input").removeClass("disabled");
    } else {
        $(".resource-requirements-input").addClass("disabled");
    }
}

function addMessagesRateOverride() {
    let template = $(".messages-rate-override-template").html();
    $(".messagesRateOverrides").append(template);
    initChildSelectPickers($(".messagesRateOverrides .messages-rate-override:last-child"));
    if (typeof refreshYaml === 'function') refreshYaml();
}

function addDataRetentionOverride() {
    let template = $(".data-retention-override-template").html();
    $(".dataRetentionOverrides").append(template);
    initChildSelectPickers($(".dataRetentionOverrides .data-retention-override:last-child"));
    if (typeof refreshYaml === 'function') refreshYaml();
}

function removeMessagesRateOverride() {
    $(this).closest(".messages-rate-override").remove();
    if (typeof refreshYaml === 'function') refreshYaml();
}

function removeDataRetentionOverride() {
    $(this).closest(".data-retention-override").remove();
    if (typeof refreshYaml === 'function') refreshYaml();
}


