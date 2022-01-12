$(document).ready(function () {
    initRefreshBulkReBalanceUrl();
    $("select").selectpicker();
    $(document).on("change, keypress, input", "input", null, refreshBulkReBalanceUrl);
    $(document).on("changed.bs.select", "select", null, refreshBulkReBalanceUrl);
    topicNames = $("#topic-names .topic-name").map(function (){
        return $(this).attr("data-topic-name");
    }).get();
    initPatternAutocomplete();
});

let topicNames = [];

function initRefreshBulkReBalanceUrl() {
    try {
        refreshBulkReBalanceUrl();
    } catch (e) {
        setTimeout(initRefreshBulkReBalanceUrl, 50);
    }
}

function initPatternAutocomplete() {
    regexInspector($("input[name=topicNamePattern]"), {
        source: topicNames
    })
}

function refreshBulkReBalanceUrl() {
    let options = extractBulkReBalanceOptions();
    let clusterIdentifier = $("meta[name=clusterIdentifier]").attr("content");
    let urlParams = {clusterIdentifier: clusterIdentifier, ...options};
    let url = urlFor("topicsManagement.showBulkReBalanceTopics", urlParams);
    $("#bulkReBalanceUrl").attr("href", url);
}

function extractBulkReBalanceOptions() {
    let totalMigrationLimit = parseInt($("input[name=totalMigrationBytesLimit]").val());
    let totalMigrationBytesLimit = "";
    if (totalMigrationLimit) {
        let dataMigrationUnit = $("select[name=totalMigrationBytesLimit-unit]").val();
        let factor;
        switch (dataMigrationUnit) {
            case "B":
                factor = 1;
                break;
            case "kB":
                factor = 1024;
                break;
            case "MB":
                factor = 1024 * 1024;
                break;
            case "GB":
                factor = 1024 * 1024 * 1024;
                break;
            case "TB":
                factor = 1024 * 1024 * 1024 * 1024;
                break;
            default:
                factor = 1;
                break;
        }
        totalMigrationBytesLimit = totalMigrationLimit * factor;
    }
    let topicNamePattern = $("input[name=topicNamePattern]").val();
    let topicNameFilterType = $("select[name=patternFilterType]").val();
    let includeTopicNamePattern = "";
    let excludeTopicNamePattern = "";
    switch (topicNameFilterType) {
        case "INCLUDE":
            includeTopicNamePattern = topicNamePattern;
            break;
        case "EXCLUDE":
            excludeTopicNamePattern = topicNamePattern;
            break;
    }
    return {
        reBalanceMode: $("select[name=reBalanceMode]").val(),
        includeTopicNamePattern: includeTopicNamePattern,
        excludeTopicNamePattern: excludeTopicNamePattern,
        topicSelectOrder: $("select[name=topicSelectOrder]").val(),
        topicBy: $("select[name=topicBy]").val(),
        topicCountLimit: $("input[name=topicCountLimit]").val(),
        topicPartitionCountLimit: $("input[name=topicPartitionCountLimit]").val(),
        totalMigrationBytesLimit: totalMigrationBytesLimit,
    };
}