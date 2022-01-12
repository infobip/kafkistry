$(document).ready(function () {
    $("#bulk-create-cluster-rules-btn").click(bulkCreateMissingPrincipalAcls);
});

function bulkCreateMissingPrincipalAcls() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Creating", "missing-rules-principal", "POST",
        "api/acls-management/create-principal-missing-acls",
        "clusterIdentifier", clusterIdentifier,
        "principal", "data-principal"
    );
}