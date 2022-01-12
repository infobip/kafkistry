function getAssignmentsData(topicName) {
    let partitions = {};
    let containerId = cleanupIdHtmlSelector("assignment-data-" + topicName);
    $("#" + containerId + " .partition").each(function () {
        let partitionId = $(this).find(".partition-id").text().trim();
        let brokerIds = [];
        $(this).find(".broker-id").each(function () {
            let brokerId = parseInt($(this).text().trim());
            brokerIds.push(brokerId);
        });
        partitions[partitionId] = brokerIds;
    });
    return partitions;
}