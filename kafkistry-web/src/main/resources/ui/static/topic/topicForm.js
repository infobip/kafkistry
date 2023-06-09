$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);
    $(document).on("changed.bs.select", "select", null, refreshYaml);

    $("#add-cluster-override-btn").click(addClusterOverride);
    $(document).on("change", "select.config-key-select", null, addConfigValue);
    $(document).on("click", ".remove-cluster-btn", null, removeClusterOverride);
    $(document).on("click", ".remove-config-btn", null, removeConfigValue);
    $(document).on("click", "input[name='propertiesOverridden']", null, togglePropertiesOverridden);
    $(document).on("click", ".move-override-up-btn", null, moveOverrideUp);
    $(document).on("click", ".move-override-down-btn", null, moveOverrideDown);

    initAutocompleteOwners(true);
    initAutocompleteProducers(true);
    $("#dry-run-inspect-btn").click(dryRunInspectConfig);
    $("input[name='propertiesOverridden']").each(togglePropertiesOverridden);
    $("input[name='resourceRequirementsDefined']").click(toggleResourceRequirementsDefined);

    initChildSelectPickers($(".apply-requirements-menu-item"));
    $("#apply-requirements-show-opts").click(function () {
        $("#apply-requirements-menu").toggle();
    });
    $("#apply-requirements-for-all").click(applyRequirementsToConfig);
    $("#apply-requirements-for-selected").click(applyRequirementsToConfigForSelected);

    $("#show-minimize-suggestion-diff-btn").click(function () {
        $("#minimize-suggestion").toggle();
    });
    $("#apply-suggested-minimization-btn").click(function () {
        let topicDescription = JSON.parse($("#minimize-suggestion-diff").attr("data-minimizedTopicDescription"));
        applyTopicDescription(topicDescription);
    });

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
        labels: extractTopicLabels(),
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
        freezeDirectives: extractFreezeDirectives(),
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
    let errors = [];
    if (topicDescription.name.trim() === "") {
        errors.push("Topic name is blank");
    }
    if (topicDescription.owner.trim() === "") {
        errors.push("Owner is blank");
    }
    if (topicDescription.description.trim() === "") {
        errors.push("Description is blank text");
    }
    if (topicDescription.producer.trim() === "") {
        errors.push("Producer is blank text");
    }
    if (topicDescription.resourceRequirements) {
        let requirementsErrors = validateResourceRequirements(topicDescription.resourceRequirements);
        requirementsErrors.forEach(function (error) {
           errors.push(error);
        });
    }
    if (JSON.stringify(topicDescriptionDryRunInspected) !== JSON.stringify(topicDescription)) {
        errors.push("Please perform 'Dry run inspect config' before saving");
    }
    return errors;
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
        tryMinimizeTopicDescription(topicDescription, yaml);
    });
}

function dryRunInspectConfig() {
    showOpProgressOnId("dryRunInspect", "Inspecting config...");
    let topicDescription = extractTopicDescription();
    if (topicDescription.resourceRequirements) {
        let errors = validateResourceRequirements(topicDescription.resourceRequirements)
        if (errors.length > 0) {
            showOpErrorOnId("dryRunInspect", "Invalid resource requirements", errors.join("\n"));
            return;
        }
    }
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
    doApplyRequirementsToConfig([], []);
}

function applyRequirementsToConfigForSelected() {
    let selectedLocations = selectedLocationsIn($(".apply-requirements-menu-item"));
    if (selectedLocations.length === 0) {
        showOpErrorOnId("applyResourceRequirementsStatus", "Nothing is selected to apply.");
        return;
    }
    let clusters = [];
    let tags = [];
    selectedLocations.forEach(function (location) {
        switch (location.type) {
            case "CLUSTER":
                clusters.push(location.value);
                break;
            case "TAG":
                tags.push(location.value);
                break;
        }
    });
    doApplyRequirementsToConfig(clusters, tags);
}

function doApplyRequirementsToConfig(onlyClusterIdentifiers, onlyClusterTags) {
    $("#apply-requirements-menu").hide();
    showOpProgressOnId("applyResourceRequirementsStatus", "Applying resource requirements to config...");
    let topicDescription = extractTopicDescription();
    $
        .ajax("api/suggestion/apply-resource-requirements" +
            "?onlyClusterIdentifiers="+onlyClusterIdentifiers.join(",") +
            "&onlyClusterTags="+onlyClusterTags.join(",")
            , {
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

function tryMinimizeTopicDescription(topicDescription, yaml) {
    $
        .ajax("api/suggestion/minimize-topic-description", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicDescription)
        })
        .done(function (response) {
            compareMinimizedTopicWithCurrent(topicDescription, yaml, response);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            console.log("Error on attempt to minimize topic description: "+ errorMsg);
        });
}

function compareMinimizedTopicWithCurrent(current, yaml, minimized) {
    function topicDescriptionWeight(topicDescription) {
        let keys = {};
        let configCount = Object.keys(topicDescription.config).length;
        Object.keys(topicDescription.perClusterProperties).forEach(function (cluster) {
            keys["cluster-" + cluster] = 1;
        });
        Object.keys(topicDescription.perClusterConfigOverrides).forEach(function (cluster) {
            keys["cluster-" + cluster] = 1;
            let configOverrides = topicDescription.perClusterConfigOverrides[cluster];
            if (configOverrides) {
                configCount += Object.keys(configOverrides).length;
            }
        });
        Object.keys(topicDescription.perTagProperties).forEach(function (tag) {
            keys["tag-" + tag] = 1;
        });
        Object.keys(topicDescription.perTagConfigOverrides).forEach(function (tag) {
            keys["tag-" + tag] = 1;
            let configOverrides = topicDescription.perTagConfigOverrides[tag];
            if (configOverrides) {
                configCount += Object.keys(configOverrides).length;
            }
        });
        return {
            overridesCount: Object.keys(keys).length,
            configCount: configCount,
            totalWeight: Object.keys(keys).length + configCount,
        };
    }
    let currentWeight = topicDescriptionWeight(current);
    let minimizedWeight = topicDescriptionWeight(minimized);
    if (minimizedWeight.totalWeight < currentWeight.totalWeight) {
        console.log("Can be minimized, old weight = " + JSON.stringify(currentWeight) + ", new weight = " + JSON.stringify(minimizedWeight));
        $("#current-num-overrides").text(currentWeight.overridesCount);
        $("#minimized-num-overrides").text(minimizedWeight.overridesCount);
        $("#current-num-configs").text(currentWeight.configCount);
        $("#minimized-num-configs").text(minimizedWeight.configCount);
        $("#minimize-form").show();
        jsonToYaml(minimized, function (minimizedYaml) {
            let diff = $("#minimize-suggestion-diff");
            diff.html(generateDiffHtml(yaml, minimizedYaml));
            diff.attr("data-minimizedTopicDescription", JSON.stringify(minimized));
        });
    } else {
        console.log("No need to minimize, old weight = " + currentWeight + ", new weight = " + minimizedWeight);
        $("#minimize-form").hide();
    }
}

function moveOverrideUp() {
    let override = $(this).closest(".cluster-override");
    let previous = override.prev();
    if (previous.length === 0) {
        return;
    }
    override.insertBefore(previous);
    refreshYaml();
}

function moveOverrideDown() {
    let override = $(this).closest(".cluster-override");
    let next = override.next();
    if (next.length === 0) {
        return;
    }
    next.insertBefore(override);
    refreshYaml();
}

