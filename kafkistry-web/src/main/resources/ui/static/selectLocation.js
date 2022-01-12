
function initSelectLocationPickers(selector) {
    $(selector).each(function () {
        initChildSelectPickers($(this));
    });
}

function initChildSelectPickers(container) {
    container.find("select[name=overrideWhere]").each(function () {
        $(this).selectpicker();
    });
}

function selectedLocationIn(dom) {
    let selectedWhere = dom.find("select[name=overrideWhere]").find(":selected");
    let whereType = selectedWhere.attr("data-type");
    let whereValue = selectedWhere.attr("value");
    return {
        type:  whereType,
        value: whereValue,
    }
}

