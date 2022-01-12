$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);
    $(document).on("changed.bs.select", "select", null, refreshYaml);
    let entityTypeSelect = $("select[name=entityType]");
    entityTypeSelect.change(adjustEntityType);
    $("#add-quota-override-btn").click(addQuotaOverride);
    $(document).on("click", ".remove-quota-override-btn", null, removeQuotaOverride);
    initAutocompleteOwners(true);
    initAutocompleteUsers(true);

    adjustEntityType.call(entityTypeSelect.get());
    initSelectLocationPickers("#quota-properties-rows .quota-override");
    resolveInitialYaml();
    refreshYaml();
});

function showWide(element) {
    let parent = element.closest("div");
    parent.removeClass("col-");
    parent.addClass("col");
    element.show();
}

function showNarrow(element) {
    let parent = element.closest("div");
    parent.removeClass("col");
    parent.addClass("col-");
    element.show();
}

function adjustEntityType() {
    let entityType = $(this).val();
    let userInput = $(".entity-user-input");
    let userDefault = $(".entity-user-default");
    let userAny = $(".entity-user-any");
    let clientInput = $(".entity-clientId-input");
    let clientDefault = $(".entity-clientId-default");
    let clientAny = $(".entity-clientId-any");
    userInput.hide();
    userDefault.hide();
    userAny.hide();
    clientInput.hide();
    clientDefault.hide();
    clientAny.hide();
    switch (entityType) {
        case "USER_CLIENT":
            showWide(userInput);
            showWide(clientInput);
            break;
        case "USER_DEFAULT_CLIENT":
            showWide(userInput);
            showNarrow(clientDefault);
            break;
        case "USER":
            showWide(userInput);
            showNarrow(clientAny);
            break;
        case "DEFAULT_USER_CLIENT":
            showNarrow(userDefault);
            showWide(clientInput);
            break;
        case "DEFAULT_USER_DEFAULT_CLIENT":
            showNarrow(userDefault);
            showNarrow(clientDefault);
            break;
        case "DEFAULT_USER":
            showNarrow(userDefault);
            showNarrow(clientAny);
            break;
        case "CLIENT":
            showNarrow(userAny);
            showWide(clientInput);
            break;
        case "DEFAULT_CLIENT":
            showNarrow(userAny);
            showNarrow(clientDefault);
            break;
    }
}

function removeQuotaOverride() {
    $(this).closest(".quota-override").remove();
    refreshYaml();
}

function addQuotaOverride() {
    let template = $("#quota-override-row-template").find("tbody").html();
    $("#quota-properties-rows").append(template);
    initChildSelectPickers($("#quota-properties-rows .quota-override:last"));
    refreshAllConfInputValues();
    refreshYaml();
}

function extractEntity() {
    let entityType = $("select[name=entityType]").val();
    let user = null;
    let clientId = null;
    let userInput = $("input[name=user]");
    let clientIdInput = $("input[name=clientId]");
    switch (entityType) {
        case "USER_CLIENT":
            user = userInput.val();
            clientId = clientIdInput.val();
            break;
        case "USER_DEFAULT_CLIENT":
            user = userInput.val();
            clientId = "<default>";
            break;
        case "USER":
            user = userInput.val();
            break;
        case "DEFAULT_USER_CLIENT":
            user = "<default>";
            clientId = clientIdInput.val();
            break;
        case "DEFAULT_USER_DEFAULT_CLIENT":
            user = "<default>";
            clientId = "<default>";
            break;
        case "DEFAULT_USER":
            user = "<default>";
            break;
        case "CLIENT":
            clientId = clientIdInput.val();
            break;
        case "DEFAULT_CLIENT":
            clientId = "<default>";
            break;
    }
    return {user: user, clientId: clientId};
}

function extractQuotaProperties(dom) {
    let producerByteRateStr = dom.find("input[name=producerByteRate]").val();
    let consumerByteRateStr = dom.find("input[name=consumerByteRate]").val();
    let requestPercentageStr = dom.find("input[name=requestPercentage]").val();
    let producerByteRate = producerByteRateStr ? parseFloat(producerByteRateStr) : null;
    let consumerByteRate = consumerByteRateStr ? parseFloat(consumerByteRateStr) : null;
    let requestPercentage = requestPercentageStr ? parseFloat(requestPercentageStr) : null;
    return {
        producerByteRate: producerByteRate,
        consumerByteRate: consumerByteRate,
        requestPercentage: requestPercentage,
    };
}

function extractEntityQuotas() {
    let presence = extractPresenceData($(".presence"));
    let quotaProperties = extractQuotaProperties($("#global-properties"));
    let clusterOverrides = {};
    let tagOverrides = {};
    $("#quota-properties-rows .quota-override").each(function () {
        let overrideRow = $(this);
        let selectedLocation = selectedLocationIn(overrideRow);
        let overrideProperties = extractQuotaProperties(overrideRow);
        switch (selectedLocation.type) {
            case "TAG":
                tagOverrides[selectedLocation.value] = overrideProperties;
                break;
            case "CLUSTER":
                clusterOverrides[selectedLocation.value] = overrideProperties;
                break;
        }
    });

    return {
        entity: extractEntity(),
        owner: $("input[name=owner]").val(),
        presence: presence,
        properties: quotaProperties,
        clusterOverrides: clusterOverrides,
        tagOverrides: tagOverrides,
    };
}

function validateQuotaProperties(which, properties) {
    if (properties.producerByteRate != null && !(properties.producerByteRate > 0)) {
        return which + ": must be > 0, got: " + properties.producerByteRate;
    }
    if (properties.consumerByteRate != null && !(properties.consumerByteRate > 0)) {
        return which + ": must be > 0, got: " + properties.consumerByteRate;
    }
    if (properties.requestPercentage != null && !(properties.requestPercentage > 0)) {
        return which + ": must be > 0, got: " + properties.requestPercentage;
    }
    if (!properties.producerByteRate && !properties.consumerByteRate && !properties.requestPercentage) {
        return which + ": must define at least one of: producerByteRate, consumerByteRate, requestPercentage";
    }
}

function validateEntityQuotas(quotas) {
    if (quotas.entity.user != null && !quotas.entity.user) {
        return "Entity's user must not be blank";
    }
    if (quotas.entity.clientId != null && !quotas.entity.clientId) {
        return "Entity's clientId must not be blank";
    }
    if (!quotas.owner) {
        return "Owner must not be blank";
    }
    let globalPropsError = validateQuotaProperties("Global-quota", quotas.properties);
    if (globalPropsError) {
        return globalPropsError;
    }
    let clusters = Object.keys(quotas.clusterOverrides);
    for (let i = 0; i < clusters.length; i++) {
        let error = validateQuotaProperties("Cluster '" + clusters[i] + "' quota", quotas.clusterOverrides[clusters[i]]);
        if (error) return error
    }
    let tags = Object.keys(quotas.tagOverrides);
    for (let i = 0; i < tags.length; i++) {
        let error = validateQuotaProperties("Tag '" + tags[i] + "' quota", quotas.tagOverrides[tags[i]]);
        if (error) return error
    }
}

function entityIdOf(entity) {
    let userStr = entity.user ? entity.user : "<all>";
    let clientIdStr = entity.clientId ? entity.clientId : "<all>";
    return userStr + "|" + clientIdStr;
}

function refreshYaml() {
    let entityQuotas = extractEntityQuotas();
    let entityID = entityIdOf(entityQuotas.entity);
    $("#filename").text("quotas/" + entityID.replace(/[^\w\-.|<>()]/, "_") + ".yaml");
    jsonToYaml(entityQuotas, function (yaml) {
        let before = initialYaml ? initialYaml : "";
        $("#config-yaml").html(generateDiffHtml(before, yaml));
    });
}


