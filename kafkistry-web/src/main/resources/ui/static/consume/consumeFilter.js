$(document).ready(function () {
    $("#filterEnabled").click(function () {
        if ($(this).is(':checked')) {
            showFilters();
        } else {
            hideFilters();
        }
    });
    $("#addRuleBtn").click(addRule);
    let filterRulesContainer = $("#filter-rules");
    filterRulesContainer.on("click", ".remove-rule-btn", null, removeRule);
    filterRulesContainer.on("change", "select[name=ruleType]", null, adjustValueVisibility);
    filterRulesContainer.on("change", "select[name=targetType]", null, targetTypeChanged);
    filterRulesContainer.on("change", "input[name=keyName]", null, setupFilterValueAutocomplete);
    filterRulesContainer.on("autocompleteselect", "input[name=keyName]", null, function () {
        let eventThis = this;
        setTimeout(function () {
            setupFilterValueAutocomplete.call(eventThis);
        }, 10);
    });
    if (readFilter) {
        adjustFiltersToInitialState(readFilter);
    } else {
        addRule();
    }
});

let recordsStructure = null;

function updateJsonFieldsAutocomplete() {
    fetchRecordsStructure();
}

function fetchRecordsStructure() {
    let cluster = $("select[name=cluster]").val();
    let topic = $("input[name=topic]").val();
    let url = "api/records-structure/topic-cluster" +
        "?topicName=" + encodeURI(topic) +
        "&clusterIdentifier=" + encodeURI(cluster);
    console.log("adjusting json fields url:" + url);
    $.get(url)
        .done(function (response) {
            recordsStructure = response;
            setupFilterFieldsAutocomplete();
        });
}

function targetTypeChanged() {
    setupFilterFieldsAutocomplete.call(this);
}

function setupFilterFieldsAutocomplete() {
    if (!recordsStructure) {
        console.log("Not having structure");
        let allKeyInputs = $(".filter-rule input[name=keyName]");
        if (allKeyInputs.autocomplete("instance")) {
            allKeyInputs.autocomplete("disable");
        }
        return;
    }
    let jsonFieldInputs = [];
    let headerFieldInputs = [];
    $(".filter-rule").each(function () {
        let ruleContainer = $(this);
        let targetType = ruleContainer.find("select[name=targetType]").val();
        let input = ruleContainer.find("input[name=keyName]");
        switch (targetType) {
            case "JSON_FIELD":
                jsonFieldInputs.push(input);
                break;
            case "RECORD_HEADER":
                headerFieldInputs.push(input);
                break;
        }
    });
    if (recordsStructure.jsonFields) {
        let fieldNames = extractFieldsNames(recordsStructure.jsonFields);
        console.log("Got json fields: " + fieldNames.length);
        enableAutocomplete(jsonFieldInputs, fieldNames);
    } else {
        console.log("Not a json fields");
        disableAutocomplete(jsonFieldInputs);
    }
    if (recordsStructure.headerFields) {
        let fieldNames = extractFieldsNames(recordsStructure.headerFields);
        console.log("Got header fields: " + fieldNames.length);
        enableAutocomplete(headerFieldInputs, fieldNames);
    } else {
        console.log("Not have header fields");
        disableAutocomplete(headerFieldInputs);
    }
}

function enableAutocomplete(inputs, fieldNames) {
    inputs.forEach(function (input) {
            let autocomplete = input
                .autocomplete({
                    source: fieldNames,
                    minLength: 0,
                    classes: {
                        "ui-autocomplete": "text-monospace, small"
                    }
                })
                .focus(function () {
                    $(this).data("uiAutocomplete").search($(this).val());
                });
            autocomplete.data("uiAutocomplete")._renderItem = function (ul, item) {
                let itemHtml = fieldAutocompleteHtml(item);
                return $("<li>")
                    .data("item.autocomplete", item)
                    .append(itemHtml)
                    .appendTo(ul);
            };
            input.autocomplete("enable");
        }
    );
}

function fieldAutocompleteHtml(item) {
    let itemHtml = "<span class='badge badge-secondary' style='width: 60px;'>"+item.type+"</span>";
    itemHtml += " <code>"+item.fullName+"</code>";
    if (item.enumerated) {
        itemHtml += " <span class='float-right badge badge-info'>ENUMERATED</span>";
    }
    if (item.nullable) {
        itemHtml += " <span class='float-right badge badge-dark'>NULLABLE</span>";
    }
    return itemHtml
}

function disableAutocomplete(inputs) {
    inputs.forEach(function (input) {
        if (input.autocomplete("instance")) {
            input.autocomplete("disable");
        }
    });
}

function extractFieldsNames(fields) {
    let result = [];
    fields.forEach(function (field) {
        Array.prototype.push.apply(result, extractFieldNames(field));
    });
    return result;
}

function extractFieldNames(field) {
    let result = [];
    if (field.fullName) {
        result.push({
            value: field.fullName,
            fullName: field.fullName,
            type: field.type,
            nullable: field.nullable,
            highCardinality: (field.value ? field.value.highCardinality : null),
            tooBig: (field.value ? field.value.tooBig : null),
            enumerated: (field.value ? !field.value.highCardinality && !field.value.tooBig : false),
            valueSet: (field.value ? field.value.valueSet : null),
        });
    }
    if (field.children) {
        Array.prototype.push.apply(result, extractFieldsNames(field.children));
    }
    return result;
}

function setupFilterValueAutocomplete() {
    let keyInput = $(this);
    let key = keyInput.val();
    let ruleContainer = keyInput.closest(".filter-rule");
    let targetType = ruleContainer.find("select[name=targetType]").val();
    let valueInput = ruleContainer.find("input[name=value]");
    let fields = [];
    if (recordsStructure) {
        switch (targetType) {
            case "JSON_FIELD":
                if (recordsStructure.jsonFields) {
                    fields = extractFieldsNames(recordsStructure.jsonFields);
                }
                break;
            case "RECORD_HEADER":
                if (recordsStructure.headerFields) {
                    fields = extractFieldsNames(recordsStructure.headerFields);
                }
                break;
        }
    }
    let valueSet = null;
    fields.forEach(function (field) {
        if (field.fullName === key) {
            valueSet = field.valueSet;
        }
    });
    if (!valueSet) {
        if (valueInput.autocomplete("instance")) {
            valueInput.autocomplete("disable");
        }
    } else {
        let stringValues = valueSet.map(function (val) {
            return val.toString();
        });
        valueInput
            .autocomplete({
                source: stringValues,
                minLength: 0,
                classes: {
                    "ui-autocomplete": "text-monospace, small"
                }
            })
            .focus(function () {
                $(this).data("uiAutocomplete").search($(this).val());
            });
        valueInput.autocomplete("enable");
    }
}

function adjustFiltersToInitialState(readFilters) {
    if (Object.keys(readFilters).length === 0) {
        addRule();
        hideFilters();
        return;
    }
    let filters = [];
    if (readFilters.all) {
        $("select[name=filtersMode]").val("ALL");
        filters = readFilters.all;
    } else if (readFilters.any) {
        $("select[name=filtersMode]").val("ANY");
        filters = readFilters.any;
    } else if (readFilters.none) {
        $("select[name=filtersMode]").val("NONE");
        filters = readFilters.none;
    }
    filters.forEach(function (filter) {
        let targetType;
        let valueRuleData;
        if (filter.jsonValueRule) {
            targetType = "JSON_FIELD";
            valueRuleData = filter.jsonValueRule;
        } else if (filter.headerValueRule) {
            targetType = "RECORD_HEADER";
            valueRuleData = filter.headerValueRule;
        } else if (filter.keyValueRule) {
            targetType = "RECORD_KEY";
            valueRuleData = filter.keyValueRule;
        } else {
            return;
        }
        let ruleForm = addRule();
        let targetTypeInput = ruleForm.find("select[name=targetType]");
        targetTypeInput.val(targetType);
        ruleForm.find("input[name=keyName]").val(valueRuleData.name);
        let ruleTypeInput = ruleForm.find("select[name=ruleType]");
        ruleTypeInput.val(valueRuleData.type);
        ruleForm.find("input[name=value]").val(valueRuleData.value);
        adjustValueVisibility.call(ruleTypeInput.get());
    });
    $("#filterEnabled").prop("checked", true);
    showFilters();
}

function hideFilters() {
    $("#filter-container").hide();
}

function showFilters() {
    $("#filter-container").show();
}

function addRule() {
    let template = $("#filter-rule-template").html();
    $("#filter-rules").append(template);
    adjustFiltersModeVisibility();
    setupFilterFieldsAutocomplete();
    return $("#filter-rules .filter-rule:last-child");
}

function removeRule() {
    $(this).closest(".filter-rule").remove();
    adjustFiltersModeVisibility();
}

function adjustValueVisibility() {
    let ruleTypeInput = $(this);
    let valueInput = ruleTypeInput.closest(".filter-rule").find("input[name=value]").closest("label");
    switch (ruleTypeInput.val()) {
        case "EXIST":
        case "NOT_EXIST":
        case "IS_NULL":
        case "NOT_NULL":
            valueInput.hide();
            break;
        default:
            valueInput.show();
            break;
    }
}

function adjustFiltersModeVisibility() {
    let numFilters = $("#filter-rules .filter-rule").length;
    if (numFilters < 2) {
        $("#filters-mode").hide();
    } else {
        $("#filters-mode").show();
    }
}

function readFilterData() {
    if (!$("#filterEnabled").is(':checked')) {
        return {};
    }
    let filters = $("#filter-rules .filter-rule").map(function () {
        let ruleForm = $(this);
        let targetType = ruleForm.find("select[name=targetType]").val();
        let valueRule = {
            name: ruleForm.find("input[name=keyName]").val(),
            type: ruleForm.find("select[name=ruleType]").val(),
            value: ruleForm.find("input[name=value]").val()
        };
        switch (targetType) {
            case 'JSON_FIELD':
                return {jsonValueRule: valueRule};
            case 'RECORD_HEADER':
                return {headerValueRule: valueRule};
            case 'RECORD_KEY':
                return {keyValueRule: valueRule};
        }
    }).get();
    if (filters.length === 0) {
        return {};
    } else {
        let filtersMode = $("select[name=filtersMode]").val();
        switch (filtersMode) {
            case "ALL":
                return {all: filters};
            case "ANY":
                return {any: filters};
            case "NONE":
                return {none: filters};
        }
    }
    return {};
}



