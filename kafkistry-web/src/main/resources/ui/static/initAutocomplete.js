$(document).ready(function () {
    allExistingOwners = existingValuesOf("#existing_owners .existing-element");
    allExistingUsers = existingValuesOf("#existing_users .existing-element");
    allExistingTopics = existingValuesOf("#existing_topics .existing-element");
    allExistingProducers = existingValuesOf("#existing_producers .existing-element");
    allExistingConsumerGroups = existingValuesOf("#existing_consumer_groups .existing-element");
    allExistingFieldClassifications = existingValuesOf("#existing_topic_field_classifications .existing-element");
});

let allExistingOwners = [];
let allExistingUsers = [];
let allExistingTopics = [];
let allExistingProducers = [];
let allExistingConsumerGroups = [];
let allExistingFieldClassifications = [];

function existingValuesOf(sourceSelector) {
    let elements = [];
    $(sourceSelector).each(function () {
        elements.push($(this).text());
    });
    return elements;
}

function initAutocomplete(elements, textInput, refreshYamlOnSelect) {
    let onSelect = refreshYamlOnSelect ? refreshYaml : undefined;
    initAutocompleteInput(elements, textInput, onSelect);
}

function initAutocompleteInput(elements, textInput, onSelectCallback) {
    let callback = function () {
        onSelectCallback.call(textInput.get());
    }
    disableAutocomplete(textInput);
    textInput
        .autocomplete({
            source: elements,
            select: function () {
                if (onSelectCallback) {
                    setTimeout(callback, 20);
                }
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
            if (onSelectCallback) {
                callback();
            }
        });
    textInput.autocomplete("enable");
}

function disableAutocomplete(textInput) {
    if (textInput.autocomplete("instance")) {
        textInput.autocomplete("disable");
    }
}

function initAutocompleteOwners(refreshYamlOnSelect) {
    initAutocomplete(allExistingOwners, $("input[name=owner]"), refreshYamlOnSelect);
}

function initAutocompleteUsers(refreshYamlOnSelect) {
    initAutocomplete(allExistingUsers, $("input[name=user]"), refreshYamlOnSelect);
}

function initAutocompleteProducers(refreshYamlOnSelect) {
    initAutocomplete(allExistingProducers, $("input[name=producer]"), refreshYamlOnSelect);
}

