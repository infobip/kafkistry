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
    let opts = extractDataTableOptions(table);
    table.css("width", "100%"); //columns.adjust() after initialized while being hidden and then shown
    let dTable = table.DataTable({
        pageLength: 10,
        bSort : opts.sortingEnabled,
        lengthMenu: [5, 10, 20, 200, 1000],
        columnDefs: opts.columnDefs,
        order: opts.orderDef.length === 0 ? undefined : opts.orderDef,
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
        stateSave: opts.saveStateEnabled,    //remember filter, sort, pagination when re-opening page
        initComplete: function () {
            hideLoader(table);
            let api = this.api();
            if (opts.hasChildDetails) {
                api.rows().every( function ( rowIdx, tableLoop, rowLoop ) {
                    let detailsHtml = opts.formatDetailsRow(api, rowIdx);
                    this.child(detailsHtml, 'child-detail no-hover');
                    registerAllInfoTooltipsIn(this.child());
                } );
            }
            table.show();
            api.columns.adjust();
            datatableInitialized(table.attr("id"));
        },
    });

    if (opts.hasChildDetails) {
        setupChildDetailsToggle(table, dTable);
        setupChildDetailsSearchHighlight(dTable);
    }
}

let initializedTables = [];
let initializedTablesListeners = [];

function extractDataTableOptions(table) {
    let columnDefs = generateDatatableColumnDefs(table);
    let detailsCellIndexes = columnDefs
        .filter(function (def) { return def.detailsCol; })
        .map(function (def) { return def.targets; });
    return {
        sortingEnabled: !table.hasClass("datatable-no-sort"),
        saveStateEnabled: !table.hasClass("datatable-no-save-state"),
        columnDefs: columnDefs,
        orderDef: generateDatatableOrderDefs(table),
        hasChildDetails: detailsCellIndexes.length > 0,
        formatDetailsRow: function (dTable, rowIdx) {
            return detailsCellIndexes
                .map(function (cellIdx) {
                    let cell = dTable.cell(rowIdx, cellIdx).node();
                    return $(cell).html();
                })
                .join("\n");
        },
    };
}

function generateDatatableColumnDefs(table) {
    return table.find("> thead > tr > th").map(function (index, th){
        let detailsCol = $(th).hasClass("details-column");
        let detailsControl = $(th).hasClass("details-toggle-column");
        return {
            className: (detailsControl ? "dt-control" : ""),
            detailsCol: detailsCol,
            targets: index,
            visible: !detailsCol,
            orderable: !$(th).hasClass("no-sort-column"),
        }
    }).get()
}

function generateDatatableOrderDefs(table) {
    return table.find("> thead > tr > th").map(function (index, th){
        let sortAsc = $(th).hasClass("default-sort-asc");
        let sortDsc = $(th).hasClass("default-sort-dsc");
        if (sortAsc) {
            return [index, "asc"];
        } else if (sortDsc) {
            return [index, "dsc"];
        }
        return null;
    }).get().filter(function (order) {
        return order != null;
    })
}

function hideLoader(table) {
    let dataTableId = table.attr("id");
    if (dataTableId) {
        $(".loading[data-table-id=" + dataTableId + "]").hide();
    } else {
        $(".loading").hide();
    }
}

function setupChildDetailsToggle(table, dTable) {
    table.find('tbody').on('click', 'tr', function () {
        let tr = $(this).closest('tr');
        let row = dTable.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
            tr.removeClass('child-shown');
        }
        else {
            row.child.show();
            let markOptions = dTable.init().mark;
            row.child().unmark(markOptions).mark(dTable.search(), markOptions);
            tr.addClass('child-shown');
        }
    });
}

function setupChildDetailsSearchHighlight(dTable) {
    dTable.on( 'search.dt', function () {
        let markOptions = dTable.init().mark;
        let searchQuery = dTable.search();
        dTable.rows('.child-shown').every( function ( rowIdx, tableLoop, rowLoop ) {
            this.child().unmark(markOptions).mark(searchQuery, markOptions);
        } );
    } );
}

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
    let hash = decodeURIComponent(window.location.hash.substring(1));
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