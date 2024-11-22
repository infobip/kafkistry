$(document).ready(function () {
    $(".add-topic-field-description-btn").click(addTopicFieldDescription);
    let descriptions = $(".field-descriptions");
    descriptions.on("click", ".remove-field-description-btn", null, removeFieldDescription);
    descriptions.on("click", ".remove-field-classification-btn", null, removeFieldClassification);
    descriptions.on("click", ".add-field-classification-btn", null, addFieldClassification);
    descriptions.on("click", ".move-up-btn", null, moveFieldDescriptionUp);
    descriptions.on("click", ".move-down-btn", null, moveFieldDescriptionDown);
    initFieldsDescriptions();
    fetchRecordsStructure();
});

/** @type {RecordStructure|null} */
let topicRecordsStructure = null;
let topicStructureValueFieldFullNames = [];
let fetchedRecordsStructureTopic = null;

function fetchRecordsStructure() {
    if (typeof extractTopicName !== "function") {
        return;
    }
    let topic = extractTopicName();
    let url = "api/records-structure/topic?topicName=" + encodeURI(topic);
    $.get(url)
        .done(function (response) {
            fetchedRecordsStructureTopic = topic;
            topicRecordsStructure = response;
            topicStructureValueFieldFullNames = topicRecordsStructure.jsonFields
                ? extractFieldsNames(topicRecordsStructure.jsonFields).map((f) => f.fullName)
                : [];
            initFieldsDescriptions();
        });
}

function initFieldsDescriptions() {
    let descriptions = $(".field-descriptions");
    descriptions.find(".topic-field-description").each(function () {
        initFieldDescriptionForm($(this));
    });
}

function initFieldDescriptionForm(fieldDescriptionForm) {
    let fieldSelectorInput = fieldDescriptionForm.find("input[name=field-selector]");
    initAutocomplete(topicStructureValueFieldFullNames, fieldSelectorInput, true);
    fieldDescriptionForm.find(".field-classification").each(function () {
        initFieldClassificationForm($(this));
    });
}

function addTopicFieldDescription() {
    let template = $("#topic-field-description-template").html();
    let descriptions = $(".field-descriptions");
    descriptions.append(template);
    let fieldDescriptionForm = descriptions.find(".topic-field-description:last");
    initFieldDescriptionForm(fieldDescriptionForm);
    maybeRefreshYaml();
}

function removeFieldDescription() {
    $(this).closest(".topic-field-description").remove();
    maybeRefreshYaml();
}

function addFieldClassification() {
    let template = $("#topic-field-classification-template").html();
    let classifications = $(this).closest(".topic-field-description").find(".field-classifications");
    classifications.append(template);
    let fieldClassificationForm = classifications.find(".field-classification:last");
    initFieldClassificationForm(fieldClassificationForm);
    fieldClassificationForm.find("input[name=field-classification]").focus();
    maybeRefreshYaml();
}

function moveFieldDescriptionUp() {
    let fieldDescription = $(this).closest(".topic-field-description");
    let previous = fieldDescription.prev();
    if (previous.length === 0) {
        return;
    }
    fieldDescription.insertBefore(previous);
    maybeRefreshYaml();
}

function moveFieldDescriptionDown() {
    let fieldDescription = $(this).closest(".topic-field-description");
    let next = fieldDescription.next();
    if (next.length === 0) {
        return;
    }
    next.insertBefore(fieldDescription);
    maybeRefreshYaml();
}


function initFieldClassificationForm(fieldClassificationForm) {
    let input = fieldClassificationForm.find("input[name=field-classification]");
    initAutocomplete(allExistingFieldClassifications, input, true)
}

function removeFieldClassification() {
    $(this).closest(".field-classification").remove();
    maybeRefreshYaml();
}

function extractTopicFieldDescriptions() {
    return $(".field-descriptions .topic-field-description").get()
        .map(function (fieldDescription) {
            let entry = $(fieldDescription);
            let selector = entry.find("input[name=field-selector]").val().trim();
            let classifications = entry.find(".field-classifications input[name=field-classification]")
                .map(function () {
                    return $(this).val();
                }).get();
            let description = entry.find("textarea[name=field-description]").val();
            return {
                selector: selector,
                classifications: classifications,
                description: description,
            };
        });
}