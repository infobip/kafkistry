$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);

    $("#add-rule-btn").click(addRule);
    let rules = $("#rules");
    rules.on("click", ".remove-rule-btn", null, removeRule);

    initAutocompleteOwners(true);
    initSelectPickers();
    initResourceNameAutocomplete();
    resolveInitialYaml();
    refreshYaml();
});

function addRule() {
    let template = $("#rule-template").html();
    $("#rules").append(template);
    let rule = $("#rules .rule:last-child");
    initSelectPicker(rule.find("select").get());
    refreshYaml();
}

function initSelectPickers() {
    $("#rules select").each(function () {
        initSelectPicker(this);
    });
}

function initResourceNameAutocomplete() {
    $("#rules select[name=resourceType]").each(function () {
        setupResourceNameAutocomplete($(this));
    });
}

function initSelectPicker(select) {
    let picker = $(select);
    picker.selectpicker();
    picker.on('changed.bs.select', refreshYaml);
    picker.on('changed.bs.select', function () {
        let select = $(this);
        if (select.attr("name") !== "resourceType") {
            return;
        }
        setupResourceNameAutocomplete(select);
    });
}


function setupResourceNameAutocomplete(typeSelect) {
    let resourceName = typeSelect.closest(".rule").find("input[name=resourceName]");
    console.log("TYPE: " + typeSelect.val(), "NAME: " + resourceName.val());
    switch (typeSelect.val()) {
        case "TOPIC":
            initAutocomplete(allExistingTopics, resourceName, true);
            break;
        case "GROUP":
            initAutocomplete(allExistingConsumerGroups, resourceName, true);
            break;
        default:
            disableAutocomplete(resourceName);
    }
}

function removeRule() {
    $(this).closest(".rule").remove();
    refreshYaml();
}

function extractPrincipalAcls() {
    let fixedPrincipalName = $("#fixed-principal").text().trim();
    let editablePrincipalName = $("input[name='principal']").val();
    let principal = (editablePrincipalName ? editablePrincipalName.trim() : fixedPrincipalName);
    let description = $("textarea[name='description']").val()
    let owner = $("input[name='owner']").val()
    let rules = [];
    $("#rules .rule").each(function () {
        let ruleDom = $(this);
        let presence = extractPresenceData(ruleDom);
        let host = ruleDom.find("input[name=host]").val();
        let resourceType = ruleDom.find("select[name=resourceType]").val();
        let resourceRawName = ruleDom.find("input[name=resourceName]").val();
        let resourceName = resourceRawName;
        let namePattern = "LITERAL";
        if (resourceRawName !== "*" && resourceRawName.endsWith("*")) {
            resourceName = resourceRawName.substring(0, resourceRawName.length - 1)
            namePattern = "PREFIXED";
        }
        let operationType = ruleDom.find("select[name=operationType]").val();
        let policy = ruleDom.find("select[name=policyType]").val();
        rules.push({
            presence: presence,
            host: host,
            resource: {
                type: resourceType,
                name: resourceName,
                namePattern: namePattern
            },
            operation: {
                type: operationType,
                policy: policy
            }
        });
    });
    return {
        principal: principal,
        description: description,
        owner: owner,
        rules: rules
    };
}

function refreshYaml() {
    let principalAcls = extractPrincipalAcls();
    $("#filename").text("acls/" + principalAcls.principal.replace(/[^\w\-.]/, "_") + ".yaml");
    jsonToYaml(principalAcls, function (yaml) {
        let before = initialYaml ? initialYaml : "";
        $("#config-yaml").html(generateDiffHtml(before, yaml));
    });
}

function validatePrincipalAcls(principalAcls) {
    let principalPattern = /[a-z]+:[^\s]/i
    if (!principalAcls.principal.match(principalPattern)) {
        return "Principal name must have pattern 'principalType:principalName'";
    }
    if (principalAcls.description.trim() === "") {
        return "Description must not be empty";
    }
    if (principalAcls.owner.trim() === "") {
        return "Owner must be specified";
    }
    if (principalAcls.rules.length === 0) {
        return "No rules specified";
    }
    for (let i = 0; i < principalAcls.rules.length; i++) {
        let rule = principalAcls.rules[i];
        if (rule.host.trim() === "") {
            return "Rule[" + i + "] has empty host";
        }
        if (!rule.resource.type) {
            return "Rule[" + i + "] resource type not defined";
        }
        if (!rule.resource.name) {
            return "Rule[" + i + "] resource name not specified";
        }
        if (!rule.operation.type) {
            return "Rule[" + i + "] operation type not defined";
        }
        if (!rule.operation.policy) {
            return "Rule[" + i + "] policy not defined";
        }
    }
    return false;
}


