$(document).ready(function () {
    $("#delete-rules-btn").click(deleteRules);
});

function deleteRules() {
    let metadata = $("#rules-metadata");
    let principal = metadata.attr("data-principal");
    let cluster = metadata.attr("data-cluster-identifier");
    let rule = metadata.attr("data-rule");
    if (rule) {
        doDeleteRule(principal, cluster, rule)
    } else {
        doDeleteRules(principal, cluster);
    }
}

function doDeleteRules(principal, cluster) {
    let url = "api/acls-management/delete-principal-acls" +
        "?principal=" + encodeURI(principal) +
        "&clusterIdentifier=" + encodeURI(cluster);
    showOpProgress("Deleting ACLs...");
    $
        .ajax(url, {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Deletion completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: " + errorMsg);
        });
}

function doDeleteRule(principal, cluster, rule) {
    let forceDelete = $("input[name=forceDelete]").is(":checked");
    let url = "api/acls-management/delete-principal-acl";
    if (forceDelete) {
        url = "api/acls-management/force-delete-principal-acl";
    }
    url += "?principal=" + encodeURI(principal) +
        "&clusterIdentifier=" + encodeURI(cluster) +
        "&rule=" + encodeURI(rule);
    showOpProgress("Deleting ACL...");
    $
        .ajax(url, {
            method: "DELETE"
        })
        .done(function () {
            showOpSuccess("Deletion completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Deletion failed: " + errorMsg);
        });
}