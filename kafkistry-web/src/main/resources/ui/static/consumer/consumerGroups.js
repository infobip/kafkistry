$(document).ready(function () {
    $(".status-filter-btn").click(filterTableByValue);
    maybeFilterDatatableByUrlHash();
});

function filterTableByValue() {
    let statusType = $(this).attr("data-filter-value");
    filterDatatableBy(statusType);
}
