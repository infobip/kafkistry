$(document).ready(function () {
    //$(document).on("change", "select.choose-label-select", null, addExistingLabelEntry);
    $("#add-freeze-directive-btn").click(addFreezeDirective);
    let directives = $(".freeze-directives");
    let pickers = directives.find("select.freezeConfigProperties");
    pickers.selectpicker();
    tweakSelectPickerBootstrapStyling(pickers);
    directives.on("click", ".remove-freeze-directive-btn", null, removeFreezeDirective);
});

function addFreezeDirective() {
    let template = $("#freeze-directive-template").html();
    $(".freeze-directives").append(template);
    let newDirective = $(".freeze-directives .freeze-directive:last");
    let pickers = newDirective.find("select.freezeConfigProperties");
    pickers.selectpicker();
    tweakSelectPickerBootstrapStyling(pickers);
    maybeRefreshYaml();
}

function removeFreezeDirective() {
    $(this).closest(".freeze-directive").remove();
    maybeRefreshYaml();
}

function maybeRefreshYaml() {
    if (typeof refreshYaml !== "undefined") {
        refreshYaml();
    }
}

function extractFreezeDirectives() {
    return $(".freeze-directives .freeze-directive").get()
        .map(function (directiveElement) {
            let directive = $(directiveElement);
            let reason = directive.find("input[name=freezeReason]").val().trim();
            let partitionsFrozen = directive.find("input[name=freezePartitionCount]").is(":checked");
            let replicationFrozen = directive.find("input[name=freezeReplicationFactor]").is(":checked");
            let configKeys = directive.find("select.freezeConfigProperties").val();
            return {
                reasonMessage: reason,
                partitionCount: partitionsFrozen,
                replicationFactor: replicationFrozen,
                configProperties: configKeys,
            };
        });
}
