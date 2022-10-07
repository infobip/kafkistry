$(document).ready(function () {
    buttonActions();
    teamNamesAutocomplete();
    producersAutocomplete();
    adjustPresenceButtonsVisibility.call($("input[name='presenceType']:checked").get());
    adjustHAButtonsVisibility.call($("input[name='highAvailability']:checked").get());
});

function createTopicFromWizard() {
    let topicCreationWizardAnswers = extractTopicCreationWizardAnswers();

    let validateErrors = validateTopicCreationWizardAnswers(topicCreationWizardAnswers);
    if (validateErrors.length > 0) {
        showOpError("Invalid input", validateErrors.join("\n"));
        return;
    }

    showOpProgress("Suggesting topic configuration...");
    $
        .ajax("api/topic-wizard/submit-answers", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicCreationWizardAnswers)
        })
        .done(function () {
            location.href = urlFor("topics.showCreateTopicFromWizard");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Wizard failed: " + errorMsg);
        });

}

function refreshGeneratedTopicName() {
    let topicNameMetadata = extractTopicNameMetadata();
    let topicNameMetaInvalid = validateTopicMetadata(topicNameMetadata);
    if (topicNameMetaInvalid) {
        generatedTopicNameError(topicNameMetaInvalid);
        return;
    }
    $
        .ajax("api/topic-wizard/generate-topic-name", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicNameMetadata)
        })
        .done(function (response) {
            $("#generatedTopicName").text(response);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            generatedTopicNameError(errorMsg);
        });
}

function generatedTopicNameError(errorMsg) {
    $("#generatedTopicName").html("<span class='text-danger'>"+errorMsg+"</span>");
}

function validateTopicCreationWizardAnswers(answers) {
    let errors = [];
    switch (answers.presence.type) {
        case "ALL_CLUSTERS":
            break;
        case "TAGGED_CLUSTERS":
            if (!answers.presence.tag) {
                errors.push("You have to select a cluster tag for chosen presence!");
            }
            break;
        default:
            if (answers.presence.kafkaClusterIdentifiers.length === 0) {
                errors.push("You have to select at least one cluster for the chosen presence!");
            }
            break;
    }
    let topicNameMetaInvalid = validateTopicMetadata(answers.topicNameMetadata);
    if (topicNameMetaInvalid) {
        errors.push(topicNameMetaInvalid);
    }
    if (!answers.teamName) {
        errors.push(errorMessage("Team name"));
    }
    if (!answers.purpose)
        errors.push(errorMessage("Purpose"));
    if (!answers.producerServiceName)
        errors.push(errorMessage("Producer"));

    // resource requirements metrics
    validateResourceRequirements(answers.resourceRequirements).forEach(function (error) {
        errors.push(error);
    });
    return errors;
}

function errorMessage(property) {
    return "'" + property + "' must be specified!";
}

function extractTopicCreationWizardAnswers() {
    let purpose = $("textarea[name='purpose']").val();
    let teamName = $("input[name='teamName']").val().trim();
    let producerServiceName = $("input[name='producer']").val().trim();
    let presence = extractPresenceData($("#presence"));
    let highAvailability = $("input[name='highAvailability']:checked").val();

    // TODO is there some way to use data class TopicCreationWizardAnswers, this could be difficult to maintain! - dpoldrugo 31.10.2019.
    return {
        purpose: purpose,
        teamName: teamName,
        producerServiceName: producerServiceName,

        topicNameMetadata: extractTopicNameMetadata(),

        resourceRequirements: extractResourceRequirements(),
        highAvailability: highAvailability,
        presence: presence
    };
}

// -------------
// UI functions
// -------------
function buttonActions() {
    $("#wizard-next-btn").click(nextButtonAction);
    $("#wizard-prev-btn").click(prevButtonAction);
    $("#wizard-go-to-create-btn").click(createTopicFromWizard);
    $("input[name=highAvailability]").change(adjustHAButtonsVisibility);
}

function teamNamesAutocomplete() {
    let teamNames = [];
    $("#owners span").each(function () {
        teamNames.push($(this).text());
    });
    $("input[name='teamName']")
        .autocomplete({source: teamNames, minLength: 0})
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}

function producersAutocomplete() {
    let producers = [];
    $("#producers span").each(function () {
        producers.push($(this).text());
    });
    $("input[name='producer']")
        .autocomplete({source: producers, minLength: 0})
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}

function adjustHAButtonsVisibility() {
    let selectedVal = $(this).val();
    let haContainer = $(this).closest(".ha-container");
    let haNoneElements = $(".ha-none");
    let haBasicElements = $(".ha-basic");
    let haAvailableElements = $(".ha-available");
    let haDurableElements = $(".ha-durable");
    let selectedClass = "bg-light font-weight-bold";
    haNoneElements.removeClass(selectedClass);
    haBasicElements.removeClass(selectedClass);
    haAvailableElements.removeClass(selectedClass);
    haDurableElements.removeClass(selectedClass);
    switch (selectedVal) {
        case "NONE":
            haNoneElements.addClass(selectedClass);
            break;
        case "BASIC":
            haBasicElements.addClass(selectedClass);
            break;
        case "STRONG_AVAILABILITY":
            haAvailableElements.addClass(selectedClass);
            break;
        case "STRONG_DURABILITY":
            haDurableElements.addClass(selectedClass);
            break;
    }
    haContainer.find("label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
}

function nextButtonAction() {
    $("#wizard-screen-1").css("display", "none");
    $("#wizard-next-btn").css("display", "none");

    $("#wizard-screen-2").css("display", "block");
    $("#wizard-prev-btn").css("display", "inline");
    $("#wizard-go-to-create-btn").css("display", "block");
}

function prevButtonAction() {
    $("#wizard-screen-2").css("display", "none");
    $("#wizard-prev-btn").css("display", "none");
    $("#wizard-go-to-create-btn").css("display", "none");

    $("#wizard-screen-1").css("display", "block");
    $("#wizard-next-btn").css("display", "inline");

}