$(document).ready(function () {
    $("#create-rules-btn").click(createRules);
});

function createRules() {
    let metadata = $("#rules-metadata");
    let principal = metadata.attr("data-principal");
    let cluster = metadata.attr("data-cluster-identifier");
    let rule = metadata.attr("data-rule");
    if (rule) {
        doCreateRule(principal, cluster, rule)
    } else {
        doCreateRules(principal, cluster);
    }
}

function doCreateRules(principal, cluster) {
    showOpProgress("Creating missing ACLs...");
    $
        .ajax("api/acls-management/create-principal-missing-acls", {
            method: "POST",
            data: {
                principal: principal,
                clusterIdentifier: cluster
            }
        })
        .done(function () {
            showOpSuccess("Creation completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}

function doCreateRule(principal, cluster, rule) {
    let forceCreate = $("input[name=forceCreate]").is(":checked");
    let url = "api/acls-management/create-missing-principal-acl";
    if (forceCreate) {
        url = "api/acls-management/force-create-missing-principal-acl";
    }
    showOpProgress("Creating missing ACL...");
    $
        .ajax(url, {
            method: "POST",
            data: {
                principal: principal,
                clusterIdentifier: cluster,
                rule: rule
            }
        })
        .done(function () {
            showOpSuccess("Creation completed with success");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Creation failed: " + errorMsg);
        });
}