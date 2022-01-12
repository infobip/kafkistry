$(document).ready(function () {
    customAssignmentBaseUrl = $("meta[name=customAssignBaseUrl]").attr("content");
    brokerIds = parseReplicas($("meta[name=brokerIds]").attr("content"));
    $("input.new-replicas-input").on("change, keypress, click, input", null, null, refreshCustomAssignmentsInput);
    refreshCustomAssignmentsInput();
});

let customAssignmentBaseUrl = null;
let brokerIds = null;

function getNewAssignments() {
    let assignments = [];
    $("input.new-replicas-input").each(function () {
        let input = $(this);
        let partition = parseInt(input.attr("data-partition"));
        assignments[partition] = parseReplicas(input.val());
    })
    return assignments;
}

function parseReplicas(replicasString) {
    return replicasString.split(",")
        .filter(function (replica) {
            return replica.trim().length > 0;
        })
        .map(function (replica) {
            return parseInt(replica.trim());
        });
}

function formatAssignment(assignments) {
    let partitions = Object.keys(assignments);
    return partitions.map(function (partition) {
        return partition + ":" + assignments[partition].join(",");
    }).join(";");
}

function refreshCustomAssignmentsInput() {
    let newAssignments = getNewAssignments();
    let assignmentsString = formatAssignment(newAssignments);
    let continueUrl = customAssignmentBaseUrl + assignmentsString;
    $("#continue-url").attr("href", continueUrl + "&back=2");
    let partitions = Object.keys(newAssignments);

    //update diff
    partitions.forEach(function (partition) {
        let newReplicas = newAssignments[partition];
        let currentReplicas = parseReplicas($(".current-replicas[data-partition=" + partition + "]").attr("data-replicas"));
        let replicasChange = $(".replicas-change[data-partition=" + partition + "]");
        if (JSON.stringify(newReplicas) === JSON.stringify(currentReplicas)) {
            replicasChange.text("---");
        } else {
            let diffHtml = generateDiffHtml(currentReplicas.join(","), newReplicas.join(","))
            replicasChange.html(diffHtml);
        }
    });

    //validate brokers
    let issues = partitions.flatMap(function (partition) {
        let newReplicas = newAssignments[partition];
        let noBrokerIssues = newReplicas.length === 0 ? ["Partition " + partition + " has no replicas"] : [];
        let unknownBrokerIssues = newReplicas.flatMap(function (brokerId) {
            let known = brokerIds.indexOf(brokerId) > -1;
            if (!known) {
                return ["unknown broker " + brokerId + " used on partition " + partition];
            } else {
                return [];
            }
        });
        let frequencies = newReplicas
            .reduce((rv, replica) => {
                rv[replica] = 1 + (rv[replica] || 0);
                return rv;
            }, {});
        let duplicateIssues = Object.keys(frequencies)
            .filter((replica) => {
                return frequencies[replica] > 1;
            })
            .map((replica) => {
                return "Partition " + partition + " has duplicated replica " + replica;
            });
        return noBrokerIssues.concat(unknownBrokerIssues).concat(duplicateIssues);
    });
    if (issues.length) {
        showOpError("Assignments validation failed", issues.join("\n"));
    } else {
        hideOpStatus();
    }
}
