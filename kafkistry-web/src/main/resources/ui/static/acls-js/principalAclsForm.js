$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);

    $("#add-rule-btn").click(addRule);
    let rules = $("#rules");
    rules.on("click", ".remove-rule-btn", null, removeRule);
    $("#dry-run-inspect-acls-btn").click(dryRunInspectAcls);

    initAutocompleteOwners(true);
    initAclSelectPickers();
    initResourceNameAutocomplete();
    resolveInitialYaml();
    refreshYaml();
});

let principalAclsDryRunInspected = null;

function addRule() {
    let template = $("#rule-template").html();
    $("#rules").append(template);
    let rule = $("#rules .rule:last-child");
    registerAllInfoTooltipsIn(rule);
    initAclSelectPicker(rule.find("select").get());
    refreshYaml();
}

function initAclSelectPickers() {
    $("#rules select").each(function () {
        initAclSelectPicker(this);
    });
}

function initResourceNameAutocomplete() {
    $("#rules select[name=resourceType]").each(function () {
        setupResourceNameAutocomplete($(this));
    });
}

function initAclSelectPicker(select) {
    let picker = $(select);
    initSelectPicker(picker);
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

function dryRunInspectAcls() {
    showOpProgressOnId("dryRunInspectAcls", "Inspecting ACLs...");
    let principalAcls = extractPrincipalAcls();
    principalAclsDryRunInspected = principalAcls;
    $
        .ajax(urlFor("acls.showDryRunInspect"), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(principalAcls)
        })
        .done(function (response) {
            hideServerOpOnId("dryRunInspectAcls");
            let resultContainer = $("#dry-run-inspect-acls-status");
            resultContainer.html(response);
            registerAllInfoTooltipsIn(resultContainer);
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId("dryRunInspectAcls");
                $("#dry-run-inspect-acls-status").html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId("dryRunInspectAcls", "Dry run of inspect failed", errorMsg);
            }
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
    if (JSON.stringify(principalAclsDryRunInspected) !== JSON.stringify(principalAcls)) {
        return "Please perform 'Dry run inspect ACLs' before saving";
    }
    return false;
}


