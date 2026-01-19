$(document).ready(function () {
    $(document).on("change", "select.choose-label-select", null, addExistingLabelEntry);
    let labelPicker = $("select.choose-label-select");
    labelPicker.selectpicker();
    tweakSelectPickerBootstrapStyling(labelPicker);
    let labels = $(".labels");
    labels.on("click", ".remove-label-btn", null, removeLabel);
    labels.on("click", ".move-label-up-btn", null, moveLabelUp);
    labels.on("click", ".move-label-down-btn", null, moveLabelDown);
});

function addExistingLabelEntry() {
    let option = $(this).closest("select").find("option:selected");
    let val = option.attr("value");
    if (!(val)) {
        return;
    }
    let category = option.attr("data-category");
    let name = option.attr("data-name");
    let externalId = option.attr("data-external-id");
    doAddLabelEntry(category, name, externalId);
    $(this).selectpicker('val', '');    //deselect picked option
}

function doAddLabelEntry(category, name, externalId) {
    let template = $("#label-entry-template").html();
    $(".labels").append(template);
    let labelEntry = $(".labels .label-entry:last")
    labelEntry.find("input[name=label-category]").val(category);
    labelEntry.find("input[name=label-name]").val(name);
    labelEntry.find("input[name=label-external-id]").val(externalId);
    maybeRefreshYaml();
}

function removeLabel() {
    $(this).closest(".label-entry").remove();
    maybeRefreshYaml();
}

function moveLabelUp() {
    let label = $(this).closest(".label-entry");
    let previous = label.prev();
    if (previous.length === 0) {
        return;
    }
    label.insertBefore(previous);
    maybeRefreshYaml();
}

function moveLabelDown() {
    let label = $(this).closest(".label-entry");
    let next = label.next();
    if (next.length === 0) {
        return;
    }
    next.insertBefore(label);
    maybeRefreshYaml();
}

function maybeRefreshYaml() {
    if (typeof refreshYaml !== "undefined") {
        refreshYaml();
    }
}

function extractTopicLabels() {
    return $(".labels .label-entry").get()
        .map(function (label) {
            let entry = $(label);
            let category = entry.find("input[name=label-category]").val().trim();
            let name = entry.find("input[name=label-name]").val().trim();
            let externalId = entry.find("input[name=label-external-id]").val().trim();
            return {
                category: category,
                name: name,
                externalId: externalId ? externalId : null,
            };
        });
}
