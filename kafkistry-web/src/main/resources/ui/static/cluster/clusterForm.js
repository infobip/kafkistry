$(document).ready(function () {
    $(".kafka-profiles").on('change.bs.select', function() {
        //make selected profiles first in select options, preserver order
        let select = $(this);
        select.find("option:selected").prependTo(select);
        select.selectpicker('refresh');
    });
    $(".add-tag-btn").click(addTag);
    $(".tags").on("click", ".remove-tag-btn", null, removeTag);
    $(document).on("change, keypress, click, input", "input, select, button", null, refreshYaml);
    $(".re-test-connection-btn").click(reTestConnection);

    existingTags = $("#existing-tags .existing-tag").map(function () {
        return $(this).attr("data-tag");
    }).get();
    initTagsAutocomplete();

    resolveInitialYaml();
    refreshYaml();
    initLastTestedInfo();
});

let existingTags = [];
let lastTestedClusterInfo = null;

function addTag() {
    let template = $("#tag-template").html();
    $(".tags").append(template);
    let tagInput = $(".tags .tag-input:last").find("input[name=tag]");
    setupTagAutocomplete(tagInput);
}

function removeTag() {
    $(this).closest(".tag-input").remove();
    refreshYaml();
}

function initTagsAutocomplete() {
    $(".tags input[name=tag]").each(function () {
        setupTagAutocomplete($(this));
    });
}

function setupTagAutocomplete(input) {
    input.autocomplete({
        source: existingTags,
        select: function () {
            setTimeout(refreshYaml, 20);
        },
        minLength: 0
    }).focus(function () {
        $(this).data("uiAutocomplete").search($(this).val());
    });
}

function initLastTestedInfo() {
    let cluster = extractClusterData();
    if (cluster.connectionString) {
        lastTestedClusterInfo = {
            connectionString: cluster.connectionString,
            usingSsl: cluster.sslEnabled,
            usingSasl: cluster.saslEnabled,
            profiles: cluster.profiles,
            clusterId: cluster.clusterId,
        };
    }
}

function extractClusterData() {
    let fixedIdentifier = $("#fixed-cluster-identifier").text().trim();
    let editableIdentifier = $("input[name=clusterIdentifier]").val();
    let clusterIdentifier = (editableIdentifier ? editableIdentifier.trim() : fixedIdentifier);
    let clusterId = $("input[name=clusterId]").val().trim();
    let connectionString = $("input[name=connectionString]").val().trim();
    let ssl = $("input[name=ssl]").is(":checked");
    let sasl = $("input[name=sasl]").is(":checked");
    let profiles = $("select[name=profiles]").val();
    let tags = $(".tags input[name=tag]").map(function (){
        return $(this).val().trim();
    }).get().filter(function (tag) {
        return tag.length > 0
    });
    return {
        identifier: clusterIdentifier,
        clusterId: clusterId,
        connectionString: connectionString,
        sslEnabled: ssl,
        saslEnabled: sasl,
        profiles: profiles,
        tags: tags
    };
}

function validateCluster(cluster) {
    if (!cluster.identifier.trim()) {
        return "Cluster identifier must not be blank";
    }
    if (!cluster.connectionString.trim()) {
        return "Connection string must not be blank";
    }
    let duplicateTags = cluster.tags.filter((tag, i, tags) => tags.indexOf(tag) !== i);
    if (duplicateTags.length > 0) {
        return "There are duplicate tags: " + duplicateTags.join(", ")
    }
    if (!lastTestedClusterInfo) {
        return "Connection needs to be tested";
    }
    if (lastTestedClusterInfo.connectionString !== cluster.connectionString) {
        return "Connection string changed since last connection test, re-test needed";
    }
    if (lastTestedClusterInfo.usingSsl !== cluster.sslEnabled) {
        return "SSL enabled changed since last connection test, re-test needed";
    }
    if (lastTestedClusterInfo.usingSasl !== cluster.saslEnabled) {
        return "SASL enabled changed since last connection test, re-test needed";
    }
    if (lastTestedClusterInfo.clusterId !== cluster.clusterId) {
        return "ClusterId changed since last connection test, re-test needed";
    }
    if (lastTestedClusterInfo.profiles.join(",") !== cluster.profiles.join(",")) {
        return "Properties profiles changed since last connection test, re-test needed";
    }
}

function testConnection(
    connectionString, ssl, sasl, profiles, successCallback
) {
    showOpProgress("Testing connection...");
    console.log("“Going to test connection: " + connectionString);
    $.get("api/clusters/test-connection", {connectionString: connectionString, ssl: ssl, sasl: sasl, profiles: profiles.join(",")})
        .done(function (clusterInfo) {
            console.log("Success, cluster info: ");
            console.log(clusterInfo);
            hideOpStatus();
            clusterInfo.usingSsl = ssl;
            clusterInfo.usingSasl = sasl;
            clusterInfo.profiles = profiles;
            successCallback(clusterInfo);
            clusterInfo.connectionString = connectionString;
            lastTestedClusterInfo = clusterInfo;
        })
        .fail(function (error) {
            console.log("Got error:");
            console.log(error);
            let errMsg = extractErrMsg(error);
            showOpError("Connection test failed", errMsg);
        })
        .always(refreshYaml);
}

function reTestConnection() {
    let cluster = extractClusterData();
    testConnection(cluster.connectionString, cluster.sslEnabled, cluster.saslEnabled, cluster.profiles,function (clusterInfo) {
        if (cluster.clusterId !== clusterInfo.clusterId) {
            showOpError("Actual and expected ClusterId do not match",
                "" +
                "actual: " + clusterInfo.clusterId + "\n" +
                "expected: " + cluster.clusterId
            );
            return;
        }
        showOpSuccess("Re-testing of connection succeeded")
    });
}

function refreshYaml() {
    let cluster = extractClusterData();
    $("#filename").text("clusters/" + cluster.identifier.replace(/[^\w\-.]/, "_") + ".yaml");
    jsonToYaml(cluster, function (yaml) {
        let before = initialYaml ? initialYaml : "";
        $("#config-yaml").html(generateDiffHtml(before, yaml));
    });
}