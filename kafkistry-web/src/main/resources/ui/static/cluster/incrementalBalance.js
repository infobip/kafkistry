$(document).ready(function () {
    initBalanceObjectivePicker($("select#balance-objective"));
    $("#incremental-migrations-btn").click(suggestIncrementalBalance);
    $("#re-assign-migrations-btn").click(performSuggestedReAssignmentMigrations);
    $(document).on("click", "input[name=migrations-display-type]", null, adjustMigrationsDisplayType);
});

function getClusterIdentifier() {
    return $("meta[name=cluster-identifier]").attr("content");
}

function initBalanceObjectivePicker(picker) {
    picker.selectpicker();
    let numOptions = picker.find("option").length;
    picker.on('changed.bs.select', function (e, clickedIndex, isSelected, previousValue) {
        let $title = $(this).parent().find('.filter-option-inner-inner');
        let selectedValues = picker.val();
        if (selectedValues.length === 0 || selectedValues.length === numOptions) {
            $title.text("ALL");
        } else if (selectedValues.length === 1) {
            $title.text(selectedValues[0]);
        } else {
            $title.text(selectedValues.length + "/" + numOptions + ": " + selectedValues.join(", "));
        }
    });
}

function adjustMigrationsDisplayType() {
    $(".migrations-display-options label").removeClass("active");
    $(".migrations-table").hide();
    let input = $(this);
    input.closest("label").addClass("active");
    $(input.val()).show();
}

function extractBalanceSettings() {
    let migrationSizeUnit = $("select[name=max-migration-size-unit]").val();
    let factor = 1;
    switch (migrationSizeUnit) {
        case "GB":
            factor = 1024 * 1024 * 1024;
            break;
        case "MB":
            factor = 1024 * 1024;
            break;
        case "kB":
            factor = 1024;
            break;
        case "B":
            factor = 1;
            break;
    }
    let maxIterations = parseInt($("input[name=max-iterations]").val());
    let timeoutTotal = 1000 * parseInt($("input[name=analyze-timeout-total]").val());
    let selectedBalancePriorities = $("select[name=balance-priority]").val();
    let priorities;
    if (selectedBalancePriorities.indexOf("EQUAL") >= 0) {
        priorities = [];
    } else {
        priorities = selectedBalancePriorities;
    }
    return {
        objective: {priorities: priorities},
        maxIterations: maxIterations,
        maxMigrationBytes: parseInt($("input[name=max-migration-size]").val()) * factor,
        timeLimitIterationMs: timeoutTotal / maxIterations,
        timeLimitTotalMs: timeoutTotal,
    };
}

function suggestIncrementalBalance() {
    let opStatusId = "suggest-migrations";
    let progressMessage = "Suggesting incremental balance migrations...";
    showOpProgressOnId(opStatusId, progressMessage);
    let clusterIdentifier = getClusterIdentifier();
    let balanceSettings = extractBalanceSettings();
    startTicking(balanceSettings.timeLimitTotalMs / 1000, opStatusId, progressMessage);
    $
        .ajax(urlFor("clusters.showIncrementalBalancingSuggestion", {clusterIdentifier: clusterIdentifier}), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(balanceSettings)
        })
        .done(function (response) {
            stopTicking();
            hideServerOpOnId(opStatusId);
            $("#migrations-container").html(response);
            initThrottleETA();
            $("#action-buttons-container").show();
        })
        .fail(function (error) {
            stopTicking();
            $("#action-buttons-container").hide();
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId(opStatusId);
                $("#migrations-container").html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId(opStatusId, "Suggesting balance migrations failed", errorMsg);
            }
        });
}

let tickingId = null;
let tickingSeconds;

function startTicking(seconds, opStatusId, message) {
    stopTicking();
    tickingSeconds = seconds;
    tickingId = setInterval(function () {
        showOpProgressOnId(opStatusId, message, "Timeout: " + tickingSeconds + " / " + seconds + " sec");
        tickingSeconds--;
    }, 1000);
}

function stopTicking() {
    if (tickingId) {
        clearInterval(tickingId);
    }
}

function performSuggestedReAssignmentMigrations() {
    applyBulkReAssignments.call(this);
}