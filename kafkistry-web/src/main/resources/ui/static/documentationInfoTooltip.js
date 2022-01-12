$(document).ready(registerAllInfoTooltips);

function registerAllInfoTooltips() {
    $.fn.popover.Constructor.Default.whiteList.table = [];
    $.fn.popover.Constructor.Default.whiteList.tr = [];
    $.fn.popover.Constructor.Default.whiteList.td = [];
    $.fn.popover.Constructor.Default.whiteList.th = [];
    $.fn.popover.Constructor.Default.whiteList.tbody = [];
    $.fn.popover.Constructor.Default.whiteList.thead = [];

    let infoIcons = [];
    $(".info-icon, .info-label").each(function () {
        let element = $(this);
        let inTemplate = element.closest(".template").length > 0;
        if (!inTemplate) {
            infoIcons.push(element);
        }
    });
    infoIcons.forEach(function (element) {
        element.tooltip();
    });
}