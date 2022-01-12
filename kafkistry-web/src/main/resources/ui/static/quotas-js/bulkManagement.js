$(document).ready(function () {
    $("#bulk-create-entity-quotas-btn").click(bulkCreateEntityQuotas);
    $("#bulk-delete-entity-quotas-btn").click(bulkDeleteEntityQuotas);
    $("#bulk-alter-entity-quotas-btn").click(bulkAlterEntityQuotas);
    $("#bulk-create-cluster-entity-quotas-btn").click(bulkCreateClusterEntityQuotas);
});

function bulkCreateEntityQuotas() {
    let quotaEntityID = $(this).attr("data-entity-id");
    performBulkOperations(
        "Creating quotas", "missing-quotas-cluster", "POST",
        "api/quotas-management/create-quotas",
        "quotaEntityID", quotaEntityID,
        "clusterIdentifier", "data-cluster-identifier"
    );
}

function bulkDeleteEntityQuotas() {
    let quotaEntityID = $(this).attr("data-entity-id");
    performBulkOperations(
        "Deleting quotas", "unwanted-quotas-cluster", "DELETE",
        "api/quotas-management/delete-quotas",
        "quotaEntityID", quotaEntityID,
        "clusterIdentifier", "data-cluster-identifier"
    );
}

function bulkAlterEntityQuotas() {
    let quotaEntityID = $(this).attr("data-entity-id");
    performBulkOperations(
        "Updating quotas", "wrong-quotas-cluster", "POST",
        "api/quotas-management/update-quotas",
        "quotaEntityID", quotaEntityID,
        "clusterIdentifier", "data-cluster-identifier"
    );
}

function bulkCreateClusterEntityQuotas() {
    let clusterIdentifier = $(this).attr("data-cluster-identifier");
    performBulkOperations(
        "Creating quotas", "missing-quotas-entity", "POST",
        "api/quotas-management/create-quotas",
        "clusterIdentifier", clusterIdentifier,
        "quotaEntityID", "data-entity-id"
    );
}
