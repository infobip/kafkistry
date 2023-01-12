$(document).ready(function () {
    $(document).on("change", "input[name=presenceType]", null, adjustPresenceButtonsVisibility);
    $(".presence select").each(function (){
        let presence = $(this);
        if (presence.closest(".template").length === 0) {
            //don't do this init if it's under template
            initSelectPicker(presence)
        }
    });
    $(document).on("click", ".opt-in-bypass-only-tag-presence-btn", null, optInEnableAllPresenceTypes);
    $(document).on("click", ".bypass-only-tag-presence-btn", null, enableAllPresenceTypes);
});

function adjustPresenceButtonsVisibility() {
    let selectedVal = $(this).val();
    let presenceContainer = $(this).closest(".presence");
    let selectedClustersContainer = presenceContainer.find(".selected-clusters");
    let selectedTagContainer = presenceContainer.find(".selected-tag");
    switch (selectedVal) {
        case "ALL_CLUSTERS":
            selectedClustersContainer.hide();
            selectedTagContainer.hide();
            break;
        case "TAGGED_CLUSTERS":
            selectedClustersContainer.hide();
            selectedTagContainer.show();
            break;
        default:
            selectedTagContainer.hide();
            selectedClustersContainer.show();
            break;
    }
    presenceContainer.find("label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
}

function extractPresenceData(dom) {
    let presenceType = dom.find("input[name=presenceType]:checked").val();
    let clusters = null;
    let tag = null;
    switch (presenceType) {
        case "ALL_CLUSTERS":
            break;
        case "TAGGED_CLUSTERS":
            tag = dom.find("select[name=selectedTag]").val();
            if (!tag) tag = null;
            break;
        default:
            clusters = dom.find("select[name=selectedClusters]").val();
            break;
    }
    return {
        type: presenceType,
        kafkaClusterIdentifiers: clusters,
        tag: tag
    };
}

function optInEnableAllPresenceTypes() {
    $(this).closest(".presence").find(".bypass-only-tag-presence-container").show();
    $(this).hide();
}

function enableAllPresenceTypes() {
    let presence = $(this).closest(".presence");
    presence.find("label.disabled").each(function () {
        $(this).removeClass("disabled");
        $(this).find("input").prop("disabled", false);
    });
    presence.find(".bypass-only-tag-presence-container").hide();
    let clusterSelectPicker = presence.find(".selected-clusters select");
    clusterSelectPicker.prop("disabled", false);
    clusterSelectPicker.selectpicker('refresh');
}
