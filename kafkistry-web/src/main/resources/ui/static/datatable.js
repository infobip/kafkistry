$(document).ready(function () {
    initDatatables();
});

function initDatatables() {
    let table = $(".datatable");
    let sortingEnabled = !table.hasClass("datatable-no-sort");
    table.DataTable({
        pageLength: 10,
        bSort : sortingEnabled,
        lengthMenu: [5, 10, 20, 200, 1000],
        oLanguage: {
            sLengthMenu: "Display _MENU_",
            sInfoEmpty: "No data to show",
            sInfo: "Got a total of _TOTAL_ to show (_START_ to _END_)",
            sInfoFiltered: " (filtering from _MAX_)",
            sZeroRecords: "No data matching search filter",
            sSearch: "Filter by anything shown:"
        },
        mark: {
            element: 'span',
            className: 'highlight'
        },
        stateSave: true,    //remember filter, sort, pagination when re-opening page
        fnInitComplete: function () {
            $(".loading").hide();
            table.show();
        }
    });
}
function maybeFilterDatatableByUrlHash() {
    if(window.location.hash) {
        let hash = window.location.hash.substring(1);
        filterDatatableBy(hash);
    }
}

function filterDatatableBy(statusType) {
    let datatable = $(".datatable");
    datatable.DataTable().search(statusType).draw();
    let element = datatable[0];
    let headerOffset = 110;
    let elementPosition = element.getBoundingClientRect().top;
    let offsetPosition = elementPosition - headerOffset;
    window.scrollTo({top: offsetPosition, behavior: "smooth"});
}