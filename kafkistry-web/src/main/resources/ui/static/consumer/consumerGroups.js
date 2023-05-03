$(document).ready(function () {
    $(".status-filter-btn").click(filterTableByValue);
    initPresetConsumerGroupForm();
    whenUrlSchemaReady(updateInitGroupButton);
    maybeFilterDatatableByUrlHash();
});

function filterTableByValue() {
    let statusType = $(this).attr("data-status-type");
    filterDatatableBy(statusType, "consumer-groups");
}

function initPresetConsumerGroupForm() {
    let groups = {};
    $(".consumer-group-row").each(function () {
        let groupId = $(this).attr("data-consumer-group-id");
        groups[groupId] = true;
    });
    let allExistingConsumerGroups = Object.keys(groups).sort();

    let consumerGroupInput = $("input[name=preset-consumer-group-id]");
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
    $("#preset-group-form select[name=preset-cluster-identifier]").on("change keyup", updateInitGroupButton);
    $("#preset-group-form input[name=preset-consumer-group-id]").on("change keyup", updateInitGroupButton);
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
