$(document).ready(function (){
    $("input[name=diff-source]").click(adjustDiffSourceButtons);
});

let initialYaml = null;

function adjustDiffSourceButtons() {
    $("#diff-against-input label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
    resolveInitialYaml();
}

function resolveInitialYaml() {
    let sourceMetadata = $("#yaml-source-metadata");
    let sourceType = sourceMetadata.attr("data-source-type");
    let objectId = sourceMetadata.attr("data-id");
    let idParamName = sourceMetadata.attr("data-id-param-name");
    let fieldName = sourceMetadata.attr("data-field-name");
    let apiPathPart = sourceMetadata.attr("data-api-path-part");
    let branchName = sourceMetadata.attr("data-branch-name");
    let objectExists = sourceMetadata.attr("data-object-exists") === 'yes';
    switch (sourceType) {
        case "NEW": {
            initialYaml = null;
            console.log("No initial source yaml");
            break;
        }
        case "EDIT": {
            initializeInitialYamlFromMaster(apiPathPart, idParamName, objectId);
            break;
        }
        case "BRANCH_EDIT": {
            $("#diff-against-input").show();
            let diffSource = $("input[name=diff-source]:checked").val();
            switch (diffSource) {
                case "master":
                    if (objectExists) {
                        initializeInitialYamlFromMaster(apiPathPart, idParamName, objectId);
                    } else {
                        initialYaml = null;
                        refreshYaml();
                    }
                    break;
                case "branch":
                    initializeInitialYamlFromBranch(apiPathPart, idParamName, objectId, branchName, fieldName);
                    break;
            }
            break;
        }
    }
}

function initializeInitialYamlFromMaster(apiPathPart, idParamName, objectId) {
    let parameters = {};
    parameters[idParamName] = objectId;
    $.get("api/" + apiPathPart + "/single", parameters)
        .done(function (response) {
            jsonToYaml(response, function (yaml) {
                initialYaml = yaml;
                console.log("Initial '" + objectId + "' yaml:");
                console.log(yaml);
                refreshYaml();
            });
        });
}

function initializeInitialYamlFromBranch(apiPathPart, idParamName, objectId, branchName, fieldName) {
    let parameters = {};
    parameters[idParamName] = objectId;
    parameters["branch"] = branchName;
    $.get("api/" + apiPathPart + "/single/pending-requests/branch", parameters)
        .done(function (response) {
            let data = response[fieldName];
            jsonToYaml(data, function (yaml) {
                initialYaml = yaml;
                console.log("Initial '" + objectId + "' on branch '" + branchName + "' yaml:");
                console.log(yaml);
                refreshYaml();
            });
        });
}

