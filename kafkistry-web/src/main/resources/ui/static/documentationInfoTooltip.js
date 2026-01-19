$(document).ready(registerAllInfoTooltips);

function registerAllInfoTooltips() {
    registerAllInfoTooltipsIn($(document));
}

function registerAllInfoTooltipsIn(container) {
    // Bootstrap 5 renamed whiteList to allowList
    const myDefaultAllowList = bootstrap.Tooltip.Default.allowList;
    myDefaultAllowList.table = [];
    myDefaultAllowList.tr = [];
    myDefaultAllowList.td = [];
    myDefaultAllowList.th = [];
    myDefaultAllowList.tbody = [];
    myDefaultAllowList.thead = [];

    let infoIcons = [];
    container.find(".info-icon, .info-label").each(function () {
        let element = $(this);
        let inTemplate = element.closest(".template").length > 0;
        if (!inTemplate) {
            infoIcons.push(element);
        }
    });
    infoIcons.forEach(function (element) {
        element.tooltip({
            html: true,
            placement: 'auto',
        });
    });
}