$(document).ready(function () {
    $(document).on("click", "[data-toggle=collapsing]", null, toggleCollapseable);
});

function toggleCollapseable(event) {
    // Prevent default behavior and stop propagation
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }

    let element = $(this);
    let targetSelector = element.attr("data-bs-target");

    if (!targetSelector) {
        console.warn("No data-bs-target attribute found on collapsing element", element);
        return;
    }

    element.toggleClass("collapsed");
    $(targetSelector).each(toggleCollapsed);
}

function toggleCollapsed() {
    let element = $(this);
    element.toggleClass("show");
}