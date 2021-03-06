$(document).ready(function () {
    initDatatables();
});

function initDatatables() {
    initDatatablesIn($(document));
}

function initDatatablesIn(container) {
    let tables = container.find("table.datatable");
    tables.each(function () {
        initDatatable($(this));
    });
}

function initDatatable(table) {
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
            let dataTableId = table.attr("id");
            if (dataTableId) {
                $(".loading[data-table-id=" + dataTableId + "]").hide();
            } else {
                $(".loading").hide();
            }
            table.show();
            datatableInitialized(table.attr("id"));
        },
    });
}

let initializedTables = [];
let initializedTablesListeners = [];

function datatableInitialized(tableId) {
    initializedTables.push(tableId);
    let readyToExec =  initializedTablesListeners.filter(function (listener) {
        return tableId === listener.tableId;
    });
    initializedTablesListeners = initializedTablesListeners.filter(function (listener) {
        return tableId !== listener.tableId;
    });
    readyToExec.forEach(function (listener) {
        listener.callback();
    });
}

function whenDatatableInitialized(tableId, callback) {
    if (initializedTables.indexOf(tableId) > -1) {
        callback();
    } else {
        initializedTablesListeners.push({tableId: tableId, callback: callback});
    }
}

function urlHashSearch() {
    if(!window.location.hash)
        return null
    let hash = window.location.hash.substring(1);
    let separatorAt = hash.indexOf("|");
    if (separatorAt === -1) {
        return {search: hash};
    } else {
        let tableId = hash.substring(0, separatorAt);
        let search = hash.substring(separatorAt + 1);
        return {search: search, tableId: tableId};
    }
}
function maybeFilterDatatableByUrlHash() {
    let searchOpts = urlHashSearch();
    if (searchOpts) {
        filterDatatableBy(searchOpts.search, searchOpts.tableId);
    }
}

function filterDatatableBy(statusType, dataTableId) {
    let datatable = dataTableId ? $(".datatable[id=" + dataTableId + "]") : $(".datatable");
    if (datatable.length === 0) {
        if (dataTableId) {
            console.error("No datatable with id='" + dataTableId + "'");
        }
        return;
    }
    let collapsingToggle = datatable.closest(".card").find("[data-toggle=collapsing]");
    if (collapsingToggle.length > 0 && collapsingToggle.hasClass("collapsed")) {
        collapsingToggle.trigger('click');
    }
    datatable.DataTable().search(statusType).draw();
    let element = datatable[0];
    let headerOffset = 250;
    let elementPosition = element.getBoundingClientRect();
    let pagePosition = document.documentElement.getBoundingClientRect();
    let yScroll = elementPosition.top - headerOffset - pagePosition.top;
    window.scrollTo({top: yScroll, behavior: "smooth"});
}