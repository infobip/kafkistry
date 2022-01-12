$(document).ready(function () {
    allExistingOwners = existingValuesOf("#existing_owners .existing-element");
    allExistingUsers = existingValuesOf("#existing_users .existing-element");
    allExistingTopics = existingValuesOf("#existing_topics .existing-element");
    allExistingProducers = existingValuesOf("#existing_producers .existing-element");
    allExistingConsumerGroups = existingValuesOf("#existing_consumer_groups .existing-element");
});

let allExistingOwners = [];
let allExistingUsers = [];
let allExistingTopics = [];
let allExistingProducers = [];
let allExistingConsumerGroups = [];

function existingValuesOf(sourceSelector) {
    let elements = [];
    $(sourceSelector).each(function () {
        elements.push($(this).text());
    });
    return elements;
}

function initAutocomplete(elements, textInput, refreshYamlOnSelect) {
    disableAutocomplete(textInput);
    textInput
        .autocomplete({
            source: elements,
            select: function () {
                if (refreshYamlOnSelect) {
                    setTimeout(refreshYaml, 20);
                }
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
            if (refreshYamlOnSelect) {
                refreshYaml();
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

