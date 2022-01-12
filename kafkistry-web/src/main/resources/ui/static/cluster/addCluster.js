$(document).ready(function () {
    $(document).on("change, keypress, click, input", "input, select, button, textarea", null, refreshYaml);
    $("#test-btn").click(initialTestConnection);
    $("#add-cluster-btn").click(addCluster);
    $("#connection").on("change, keypress, click, input", "", null, detectSsl);
});

function detectSsl() {
    let connection = $(this).val().trim();
    if (connection.endsWith(":9093")) {
        $("#ssl").prop("checked", true);
    } else if (connection.endsWith(":9092")) {
        $("#ssl").prop("checked", false);
    }
}

function initialTestConnection() {
    let connectionString = $("#connection").val().trim();
    let ssl = $("#ssl").is(":checked");
    let sasl = $("#sasl").is(":checked");
    let profiles = $("#profiles").val();
    testConnection(connectionString, ssl, sasl, profiles, advanceToSaveStep);
}

function advanceToSaveStep(clusterInfo) {
    $("input[name=clusterId]").val(clusterInfo.clusterId);
    $("input[name=connectionString]").val(clusterInfo.connectionString);
    $("input[name=ssl]").prop("checked", clusterInfo.usingSsl);
    $("input[name=sasl]").prop("checked", clusterInfo.usingSasl);
    let profilesSelect = $("select[name=profiles]");
    for (let i = clusterInfo.profiles.length - 1; i >= 0; i--) {
        //make selected profiles first in select options, preserver order
        let profile = clusterInfo.profiles[i];
        let profileOption = profilesSelect.find("option[value='" + profile + "']");
        profileOption.prop("selected", true);
        profileOption.prependTo(profilesSelect);
    }
    profilesSelect.selectpicker('refresh');
    $(".test-connection-step").hide();
    $(".save-step").show();
}

function addCluster() {
    showOpProgress("Adding new cluster...");
    let clusterData = extractClusterData();
    let validateErr = validateCluster(clusterData);
    if (validateErr) {
        showOpError(validateErr);
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (!updateMsg.trim()) {
        updateMsg = "Initial cluster add: " + clusterData.identifier;
    }
    console.log("Adding new cluster identifier=" + clusterData.identifier + " connection=" + clusterData.connectionString);
    $
        .ajax("api/clusters?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(clusterData)
        })
        .done(function () {
            showOpSuccess("Successfully added cluster to registry");
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpError("Got error while trying to add cluster", errMsg);
        });
}

