$(document).ready(function () {
    $("#bulk-create-principal-rules-btn").click(bulkCreateWhereMissingAcls);
});

function bulkCreateWhereMissingAcls() {
    let principal = $(this).attr("data-principal");
    performBulkOperations(
        "Creating", "missing-rules-cluster", "POST",
        "api/acls-management/create-principal-missing-acls",
        "principal", principal,
        "clusterIdentifier", "data-cluster-identifier"
    );
}