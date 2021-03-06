$(document).ready(function () {
    $.fn.selectpicker.Constructor.DEFAULTS.whiteList.span.push('title');
    $(document).on("change", "input[name=presenceType]", null, adjustPresenceButtonsVisibility);
    $(".presence select").each(function (){
        let presence = $(this);
        if (presence.closest(".template").length === 0) {
            presence.selectpicker();    //don't do this init if it's under template
        }
    });
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
