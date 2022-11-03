
function initSelectLocationPickers(selector) {
    $(selector).each(function () {
        initChildSelectPickers($(this));
    });
}

function initChildSelectPickers(container) {
    container.find("select[name=overrideWhere]").each(function () {
        initSelectPicker($(this));
    });
}

function selectedLocationsIn(dom) {
    let selectedWhereItems = dom.find("select[name=overrideWhere]").find(":selected");
    let result = [];
    selectedWhereItems.each(function () {
        result.push(selectedLocationOf($(this)));
    });
    return result;
}

function selectedLocationIn(dom) {
    let selectedWhere = dom.find("select[name=overrideWhere]").find(":selected");
    return selectedLocationOf(selectedWhere);
}

function selectedLocationOf(selectedWhere) {
    let whereType = selectedWhere.attr("data-type");
    let whereValue = selectedWhere.attr("value");
    return {
        type:  whereType,
        value: whereValue,
    }
}

