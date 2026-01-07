$(document).ready(function () {
    loadAllConsumerGroupsTable();
});

function loadAllConsumerGroupsTable() {
    showOpProgressOnId("allConsumerGroups", "Loading consumer groups...");
    whenUrlSchemaReady(function () {
        let url = urlFor("consumerGroups.showConsumerGroupsTable");
        let allConsumerGroupsResultContainer = $("#all-consumer-groups-result");
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnId("allConsumerGroups");
                allConsumerGroupsResultContainer.html(response);
                refreshAllConfValuesIn(allConsumerGroupsResultContainer);
                registerAllInfoTooltipsIn(allConsumerGroupsResultContainer);
                // Extract groups BEFORE datatable initialization (so we get all rows, not just first page)
                let allConsumerGroups = extractAllConsumerGroups();
                initDatatablesIn(allConsumerGroupsResultContainer);
                // Setup filter buttons after content is loaded
                $(".status-filter-btn").click(filterTableByValue);
                initPresetConsumerGroupForm(allConsumerGroups);
                whenUrlSchemaReady(updateInitGroupButton);
                maybeFilterDatatableByUrlHash();
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId("allConsumerGroups");
                    allConsumerGroupsResultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId("allConsumerGroups", "Failed to get consumer groups", errorMsg);
                }
            });
    });
}

function filterTableByValue() {
    let statusType = $(this).attr("data-status-type");
    filterDatatableBy(statusType, "consumer-groups");
}

function extractAllConsumerGroups() {
    let groups = {};
    $(".consumer-group-row").each(function () {
        let groupId = $(this).attr("data-consumer-group-id");
        groups[groupId] = true;
    });
    return Object.keys(groups).sort();
}

function initPresetConsumerGroupForm(allExistingConsumerGroups) {

    let consumerGroupInput = $("#preset-group-form input[name=preset-consumer-group-id]");
    consumerGroupInput.on("change keyup", updateInitGroupButton);
    consumerGroupInput
        .autocomplete({
            source: allExistingConsumerGroups,
            select: function () {
                setTimeout(updateInitGroupButton, 20);
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
    let clusterSelectPicker = $("#preset-group-form select[name=preset-cluster-identifier]");
    initSelectPicker(clusterSelectPicker);
    clusterSelectPicker.on("change keyup", updateInitGroupButton);
    consumerGroupInput.on("change keyup", updateInitGroupButton);
}

function updateInitGroupButton() {
    let initBtn = $("#init-consumer-group-btn");
    let clusterIdentifier = $("select[name=preset-cluster-identifier]").val();
    let consumerGroup = $("input[name=preset-consumer-group-id]").val();
    let valid = clusterIdentifier && consumerGroup;
    if (!valid) {
        initBtn.attr("href", "");
        initBtn.addClass("disabled");
        return;
    }
    let urlInitGroup = urlFor("consumerGroups.showPresetConsumerGroupOffsets", {
        clusterIdentifier: clusterIdentifier,
        consumerGroupId: consumerGroup,
    });
    initBtn.attr("href", urlInitGroup);
    initBtn.removeClass("disabled");
}
