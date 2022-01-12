$(document).ready(function () {
    $(document).on("click", "[data-toggle=collapsing]", null, toggleCollapseable);
});

function toggleCollapseable() {
    let element = $(this);
    let targetSelector = element.attr("data-target");
    element.toggleClass("collapsed")
    $(targetSelector).each(toggleCollapsed);
}

function toggleCollapsed() {
    let element = $(this);
    element.toggleClass("show");
}