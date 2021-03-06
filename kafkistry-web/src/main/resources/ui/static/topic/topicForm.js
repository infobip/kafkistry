$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);
    $(document).on("changed.bs.select", "select", null, refreshYaml);

    $("#add-cluster-override-btn").click(addClusterOverride);
    $(document).on("change", "select.config-key-select", null, addConfigValue);
    $(document).on("click", ".remove-cluster-btn", null, removeClusterOverride);
    $(document).on("click", ".remove-config-btn", null, removeConfigValue);
    $(document).on("click", "input[name='propertiesOverridden']", null, togglePropertiesOverridden);

    initAutocompleteOwners(true);
    initAutocompleteProducers(true);
    $("#dry-run-inspect-btn").click(dryRunInspectConfig);
    $("input[name='propertiesOverridden']").each(togglePropertiesOverridden);
    $("input[name='resourceRequirementsDefined']").click(toggleResourceRequirementsDefined);
    $("#applyRequirementsToConfig").click(applyRequirementsToConfig);
    initSelectLocationPickers("#clusters .cluster-override");
    initSelectConfigPropertyPickers(".globalConfig");
    initSelectConfigPropertyPickers("#clusters .cluster-override");
    resolveInitialYaml();
    refreshYaml();
});

let topicDescriptionDryRunInspected = null;

function extractTopicDescription() {
    let fixedTopicName = $("#yaml-source-metadata").attr("data-id");
    let editableTopicName = $("input[name='topicName']").val();
    let topicName = (editableTopicName ? editableTopicName.trim() : fixedTopicName);
    let owner = $("input[name='owner']").val().trim();
    let description = $("textarea[name='description']").val();
    let producer = $("input[name='producer']").val().trim();
    let presence = extractPresenceData($("#presence"));
    let partitionCount = $(".globalConfig input[name='partitionCount']").val();
    let replicationFactor = $(".globalConfig input[name='replicationFactor']").val();
    let config = {};
    $(".globalConfig .config-entry .conf-value-in").toArray().map(function (input) {
        config[input.name] = input.value;
    });
    let perClusterProperties = {};
    let perClusterConfig = {};
    let perTagProperties = {};
    let perTagConfig = {};
    $("#clusters .cluster-override").each(function () {
        let element = $(this);
        let selectLocation = selectedLocationIn(element);
        let overridePartitionCount = element.find("input[name='partitionCount']").val();
        let overrideReplicationFactor = element.find("input[name='replicationFactor']").val();
        let propertiesOverridden = element.find("input[name='propertiesOverridden']").is(":checked");
        let properties = propertiesOverridden
            ? {
                partitionCount: parseInt(overridePartitionCount),
                replicationFactor: parseInt(overrideReplicationFactor)
            }
            : undefined;
        let clusterConfig = {};
        element.find(".config-entry .conf-value-in").toArray().map(function (input) {
            clusterConfig[input.name] = input.value;
        });
        clusterConfig = Object.keys(clusterConfig).length > 0 ? clusterConfig : undefined;
        switch (selectLocation.type) {
            case "TAG":
                perTagProperties[selectLocation.value] = properties;
                perTagConfig[selectLocation.value] = clusterConfig;
                break;
            case "CLUSTER":
                perClusterProperties[selectLocation.value] = properties;
                perClusterConfig[selectLocation.value] = clusterConfig;
                break;
        }
    });
    return {
        name: topicName,
        owner: owner,
        description: description,
        resourceRequirements: extractResourceRequirements(),
        producer: producer,
        presence: presence,
        properties: {
            partitionCount: parseInt(partitionCount),
            replicationFactor: parseInt(replicationFactor)
        },
        config: config,
        perClusterProperties: perClusterProperties,
        perClusterConfigOverrides: perClusterConfig,
        perTagProperties: perTagProperties,
        perTagConfigOverrides: perTagConfig,
    };
}

function applyTopicDescription(topicDescription) {
    console.log("EXPECTED:\n"+JSON.stringify(topicDescription, null, 4));
    console.log(topicDescription);
    console.log("Going to apply topicDescription");

    //metadata
    $("#topicName").text(topicDescription.name);
    $("input[name='topicName']").val(topicDescription.name);
    $("input[name='owner']").val(topicDescription.owner);
    $("textarea[name='description']").val(topicDescription.description);
    $("input[name='producer']").val(topicDescription.producer);

    //TODO implement setting of topicPresence and resourceRequirements here if it will be needed

    //clear all
    $(".globalConfig .config-entry").each(function () {
        $(this).remove()
    });
    $("#clusters .cluster-override").each(function () {
        $(this).remove()
    });

    //setup global properties
    $(".globalConfig input[name='partitionCount']").val(topicDescription.properties.partitionCount);
    $(".globalConfig input[name='replicationFactor']").val(topicDescription.properties.replicationFactor);

    //setup global config
    let globalConfig = $(".globalConfig .config");
    Object.keys(topicDescription.config).forEach(function (key) {
        let value = topicDescription.config[key];
        addConfigEntry(globalConfig, key, value);
    });

    //setup cluster config overrides
    Object.keys(topicDescription.perClusterConfigOverrides).forEach(function (clusterIdentifier) {
       let overrideContainer = ensureClusterOverride("CLUSTER", clusterIdentifier).find(".config");
       injectConfigToOverrideContainer(overrideContainer, topicDescription.perClusterConfigOverrides[clusterIdentifier]);
    });
    Object.keys(topicDescription.perTagConfigOverrides).forEach(function (tag) {
       let overrideContainer = ensureClusterOverride("TAG", tag).find(".config");
       injectConfigToOverrideContainer(overrideContainer, topicDescription.perTagConfigOverrides[tag]);
    });

    //setup cluster properties overrides
    Object.keys(topicDescription.perClusterProperties).forEach(function (clusterIdentifier) {
        let overrideContainer = ensureClusterOverride("CLUSTER", clusterIdentifier);
        injectPropertiesToOverrideContainer(overrideContainer, topicDescription.perClusterProperties[clusterIdentifier]);
    });
    Object.keys(topicDescription.perTagProperties).forEach(function (tag) {
        let overrideContainer = ensureClusterOverride("TAG", tag);
        injectPropertiesToOverrideContainer(overrideContainer, topicDescription.perTagProperties[tag]);
    });

    refreshYaml();

    //check if consistent
    let appliedTopicDescription = extractTopicDescription();
    let expectedJson = JSON.stringify(topicDescription, null, 4);
    let actualJson = JSON.stringify(appliedTopicDescription, null, 4);
    if (expectedJson === actualJson) {
        console.log("Applying resulted in expected description");
    } else {
        console.log("ACTUAL:\n"+actualJson);
        console.log("Applying created miss-match of description");
    }
}

function injectPropertiesToOverrideContainer(overrideContainer, properties) {
    overrideContainer.find("input[name=partitionCount]").val(properties.partitionCount);
    overrideContainer.find("input[name=replicationFactor]").val(properties.replicationFactor);
    let overriddenCheckbox = overrideContainer.find("input[name=propertiesOverridden]").prop("checked", true).get();
    togglePropertiesOverridden.call(overriddenCheckbox);
}

function injectConfigToOverrideContainer(overrideContainer, configs) {
    Object.keys(configs).forEach(function (key) {
        let value = configs[key];
        addConfigEntry(overrideContainer, key, value);
    });
}

function ensureClusterOverride(whereType, whereValue) {
    console.log("Finding: "+whereType+":"+whereValue);
    let overrideContainer = null;
    $("#clusters .cluster-override").each(function (){
        let someContainer = $(this);
        let selectedWhere = someContainer.find("select[name='overrideWhere']").find(":selected");
        let someWhereType = selectedWhere.attr("data-type");
        let someWhereValue = selectedWhere.attr("value");
        if (someWhereType === whereType && someWhereValue === whereValue) {
            //found existing override
            console.log("Found: "+whereType+":"+whereValue);
            overrideContainer = someContainer;
        }
    });
    //do not create duplicate
    if (overrideContainer != null) {
        console.log("Retuning found: "+whereType+":"+whereValue);
        return overrideContainer;
    }
    console.log("Creating new container: "+whereType+":"+whereValue);
    addClusterOverride(true);
    overrideContainer = $("#clusters .cluster-override:last");
    let whereSelect = overrideContainer.find("select[name=overrideWhere]");
    whereSelect.find("option[data-type='"+whereType+"'][value='" + whereValue + "']").prop("selected", true);
    whereSelect.selectpicker('refresh');
    console.log("Returning new container: "+whereType+":"+whereValue);
    return overrideContainer;
}

function addConfigEntry(configContainer, key, value) {
    let adder = configContainer.find("select.config-key-select option[value='" + key+"']");
    adder.prop('selected', true);
    addConfigValue.call(adder, null, true, value);
}

function validateTopicDescription(topicDescription) {
    if (topicDescription.name.trim() === "") {
        return "Topic name is blank";
    }
    if (topicDescription.owner.trim() === "") {
        return "Owner is blank";
    }
    if (topicDescription.description.trim() === "") {
        return "Description is blank text";
    }
    if (topicDescription.producer.trim() === "") {
        return "Producer is blank text";
    }
    if (JSON.stringify(topicDescriptionDryRunInspected) !== JSON.stringify(topicDescription)) {
        return "Please perform 'Dry run inspect config' before saving";
    }
    return false;
}

function addConfigValue(event, preventRefreshYaml, predefinedValue) {
    let option = $(this).closest("tr").find("option:selected");
    let key = option.attr("value");
    if (!(key)) {
        return;
    }
    let value = option.attr("data-value");
    if (predefinedValue !== undefined) {
        value = predefinedValue;
    }
    let doc = option.attr("data-doc");
    let configValues = $(this).closest(".config");
    let configEntryTemplate = $("#config-entry-template").text();
    let configEntry = configEntryTemplate
        .replace(/%key-PH%/g, key)
        .replace(/%value-PH%/g, value)
        .replace(/%doc-PH%/g, doc);
    configValues.append(configEntry);
    registerAllInfoTooltips();
    let inElement = configValues.find(".config-entry:last-child input").focus().get().slice(-1)[0];
    $(this).selectpicker('val', '');    //deselect picked option
    refreshInputConfigValue.call(inElement, true);
    if (preventRefreshYaml === undefined) refreshYaml();
}

function removeConfigValue() {
    $(this).closest("tr").remove();
    refreshYaml();
}

function addClusterOverride(preventRefreshYaml) {
    let template = $("#cluster-override-template").html();
    $("#clusters").append(template);
    registerAllInfoTooltips();
    let lastOverrideContainer = $("#clusters .cluster-override:last");
    initChildSelectPickers(lastOverrideContainer);
    initChildConfigPropertyPickers(lastOverrideContainer);
    if (preventRefreshYaml === undefined) refreshYaml();
}

function removeClusterOverride() {
    $(this).closest(".cluster-override").remove();
    refreshYaml();
}

function initSelectConfigPropertyPickers(selector) {
    $(selector).each(function () {
        initChildConfigPropertyPickers($(this));
    });
}

function initChildConfigPropertyPickers(container) {
    container.find("select.config-key-select").each(function () {
        $(this).selectpicker();
    });
}

function togglePropertiesOverridden() {
    let overridden = $(this).is(":checked");
    let parent = $(this).closest(".cluster-override");
    parent.find(".properties-row input[type='number']").attr("disabled", !overridden);
    if (overridden) {
        parent.find(".properties-row").css("color", "black");
    } else {
        parent.find(".properties-row").css("color", "gray");
    }
}

function refreshYaml() {
    let topicDescription = extractTopicDescription();
    $("#filename").text("topics/" + topicDescription.name.replace(/[^\w\-.]/, "_") + ".yaml");
    jsonToYaml(topicDescription, function (yaml) {
        let before = initialYaml ? initialYaml : "";
        $("#config-yaml").html(generateDiffHtml(before, yaml));
    });
}

function dryRunInspectConfig() {
    showOpProgressOnId("dryRunInspect", "Inspecting config...");
    let topicDescription = extractTopicDescription();
    topicDescriptionDryRunInspected = topicDescription;
    $
        .ajax(urlFor("topics.showDryRunInspect"), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(topicDescription)
        })
        .done(function (response) {
            hideServerOpOnId("dryRunInspect");
            $("#dry-run-inspect-status").html(response);
            registerAllInfoTooltips();
            refreshAllConfValues();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId("dryRunInspect");
                $("#dry-run-inspect-status").html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId("dryRunInspect", "Dry run of inspect failed", errorMsg);
            }
        });
}

function applyRequirementsToConfig() {
    showOpProgressOnId("applyResourceRequirementsStatus", "Applying resource requirements to config...");
    let topicDescription = extractTopicDescription();
    $
        .ajax("api/suggestion/apply-resource-requirements", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicDescription)
        })
        .done(function (response) {
            applyTopicDescription(response);
            hideServerOpOnId("applyResourceRequirementsStatus");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpErrorOnId("applyResourceRequirementsStatus", "Applying of resource requirements to config failed", errorMsg);
        });
}

