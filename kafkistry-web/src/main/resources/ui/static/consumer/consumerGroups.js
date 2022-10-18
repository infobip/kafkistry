$(document).ready(function () {
    $(".status-filter-btn").click(filterTableByValue);
    maybeFilterDatatableByUrlHash();
});

function filterTableByValue() {
    let statusType = $(this).attr("data-status-type");
    filterDatatableBy(statusType, "consumer-groups");
}
