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

/**
 * Filters and sorts autocomplete suggestions, prioritizing items that start with the search term.
 * @param {Array} items - Array of strings to filter
 * @param {string} searchTerm - The search term to filter by
 * @returns {Array} Filtered and sorted array with startsWith matches first
 */
function filterAutocompleteSuggestions(items, searchTerm) {
    if (!searchTerm) {
        return items;
    }
    const lowerSearchTerm = searchTerm.toLowerCase();
    const startsWith = [];
    const contains = [];

    items.forEach(item => {
        const lowerItem = item.toLowerCase();
        if (lowerItem.includes(lowerSearchTerm)) {
            if (lowerItem.startsWith(lowerSearchTerm)) {
                startsWith.push(item);
            } else {
                contains.push(item);
            }
        }
    });

    // Return items that start with search term first, then items that contain it
    return startsWith.concat(contains);
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
            source: function(request, response) {
                const filtered = filterAutocompleteSuggestions(elements, request.term);
                response(filtered);
            },
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

