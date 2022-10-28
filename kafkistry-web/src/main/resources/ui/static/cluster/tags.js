$(document).ready(function () {
    let tagsTable = $("#tags-table");
    tagsTable.on("click", ".tag-marker-btn", null, tagMarkerClicked);
    tagsTable.on("input", "input[name=new-tag-name]", null, tagNameEdited);
    tagsTable.on("click", ".remove-tag-btn", null, removeNewTag);
    $("input[name=tag-filter]").on("input", tagClusterFilterChanged);
    $("input[name=cluster-filter]").on("input", tagClusterFilterChanged);
    loadClusters();
    console.log("all clusters", allClusters, enabledClusters);
    console.log("enabled clusters", enabledClusters);
    $("#add-new-tag-btn").click(addNewTag);
    $("#dry-run-all-btn").click(dryRunInspectAllClusters);
    $("#expand-collapse-all-dry-run-btn").click(expandCollapseAllDryRuns);
    $("#save-clusters-btn").click(saveClustersChanges);
    refreshEditedClusterTags();
});

let allClusters = {};
let enabledClusters = [];
let dryRunInspectedClusters = {};
let tagPrefixRegex = /^([a-zA-Z]+.?).*/;

function tagPrefixOf(tag) {
    let match = tag.match(tagPrefixRegex);
    return match ? match[1] : undefined;
}

function loadClusters() {
    let clusters = {};
    $(".cluster-json").each(function () {
        let clusterJson = $(this).attr("data-cluster-json");
        let cluster = JSON.parse(clusterJson);
        clusters[cluster.identifier] = cluster;
    });
    allClusters = clusters;
    enabledClusters = $(".enabled-cluster-identifier").map(function () {
        return $(this).attr("data-cluster-identifier");
    }).get();
}

function addNewTag() {
    let templateHtml = $("#tag-template-table tbody").html();
    $("#tags-table tbody").append($(templateHtml));
    let tagInput = $("#tags-table .tag-row:last-child").find("input[name=new-tag-name]");
    tagClusterFilterChanged();
    refreshEditedClusterTags();
    let autocompleteTags = {};
    $("#tags-table .tag-row").get().forEach(function (row) {
        let tag = $(row).attr("data-tag");
        autocompleteTags[tagPrefixOf(tag)] = true;
    });
    initAutocomplete(Object.keys(autocompleteTags), tagInput);
}

function removeNewTag() {
    $(this).closest(".tag-row").remove();
    refreshEditedClusterTags();
}

function tagNameEdited() {
    let tag = $(this).val();
    let row = $(this).closest(".tag-row");
    row.attr("data-tag", tag);
    row.find(".tag-marker-btn").attr("data-tag", tag);
    refreshEditedClusterTags();
}

function tagMarkerClicked() {
    let btn = $(this);
    let wasActive = btn.hasClass("active");
    let initiallyTagged = btn.attr("data-tagged") === "yes";
    let tagged = !wasActive;
    btn.removeClass("btn-outline-primary");
    btn.removeClass("btn-outline-secondary");
    btn.removeClass("btn-outline-danger");
    btn.removeClass("btn-outline-success");
    if (wasActive) {
        btn.removeClass("active");
    } else {
        btn.addClass("active");
    }
    if (initiallyTagged && !tagged) {
        btn.addClass("btn-outline-danger");
    } else if (!initiallyTagged && tagged) {
        btn.addClass("btn-outline-success");
    } else if (initiallyTagged && tagged) {
        btn.addClass("btn-outline-primary");
    } else {
        btn.addClass("btn-outline-secondary");
    }
    refreshEditedClusterTags();
}

function tagClusterFilterChanged() {
    function tokensOf(input) {
        return input.val().trim().split(" ")
            .map(function (token) {
                return token.trim().toLowerCase();
            })
            .filter(function (token) {
                return token.length > 0;
            });
    }
    function matches(text, tokens) {
        if (tokens.length === 0) return true;
        for (let i = 0; i < tokens.length; i++) {
            if (text.toLowerCase().indexOf(tokens[i]) > -1) return true;
        }
        return false;
    }
    let tagTokens = tokensOf($("input[name=tag-filter]"));
    let clusterTokens = tokensOf($("input[name=cluster-filter]"));

    function filterRow(row) {
        let hasMatchingMarkers = false;
        row.find(".tag-marker-btn").each(function () {
            let tagMarker = $(this);
            if (matches(tagMarker.attr("data-cluster"), clusterTokens)) {
                hasMatchingMarkers = true;
                tagMarker.show();
            } else {
                tagMarker.hide();
            }
        });
        let tagMatches = matches(row.attr("data-tag"), tagTokens) || row.hasClass("new-tag");
        return tagMatches && hasMatchingMarkers;
    }

    $("#tags-table .tag-row").each(function () {
        let row = $(this);
        if (filterRow(row)) {
            row.show();
        } else {
            row.hide();
        }
    });
}

function dryRunInspectAllClusters() {
    $("#dry-run-all-result").show();
    console.log("going to dry-run inspect all clusters");
    showOpProgressOnId("cluster-bulk-dry-run", "Dry-run inspecting clusters...")
    Object.keys(allClusters).forEach(function (clusterIdentifier) {
        if (enabledClusters.indexOf(clusterIdentifier) < 0) {
            return;
        }
        doClusterDryRunInspect(
            "cluster-dry-run-inspect-" + clusterIdentifier,
            $(".cluster-dry-run-inspect-result[data-cluster-identifier=" + clusterIdentifier + "]"),
            $(".cluster-dry-run-inspect-summary[data-cluster-identifier=" + clusterIdentifier + "]"),
            function () {
                let cluster = extractEditedCluster(clusterIdentifier);
                dryRunInspectedClusters[clusterIdentifier] = cluster;
                return cluster;
            }
        );
    });
}

function extractEditedCluster(clusterIdentifier) {
    let originalCluster = allClusters[clusterIdentifier];
    let editedCluster = JSON.parse(JSON.stringify(originalCluster)); //deep copy
    let oldTags = originalCluster.tags;
    let newTags = [];
    $("#tags-table .tag-marker-btn[data-cluster=" + clusterIdentifier + "]").each(function () {
        if ($(this).hasClass("active")) {
            newTags.push($(this).attr("data-tag"));
        }
    });
    editedCluster.tags = computeOrderedTags(oldTags, newTags);
    return editedCluster;
}

function computeOrderedTags(oldTags, newTags) {
    let tagPrefixes = [];   //try to keep order of new tags by prefix
    oldTags.forEach(function (tag) {
        let prefix = tagPrefixOf(tag);
        if (prefix && tagPrefixes.indexOf(prefix) < 0) {
            tagPrefixes.push(prefix);
        }
    });
    newTags.forEach(function (tag) {
        let prefix = tagPrefixOf(tag);
        if (prefix && tagPrefixes.indexOf(prefix) < 0) {
            tagPrefixes.push(prefix);
        }
    });
    let editedTags = oldTags.filter(function (tag) {
        return newTags.indexOf(tag) > -1;
    });
    newTags.forEach(function (tag) {
        if (oldTags.indexOf(tag) < 0) {
            editedTags.push(tag);
        }
    });
    let orderedEditedTags = [];
    tagPrefixes.forEach(function (prefix) {
        editedTags.filter(function (tag) {
            return tag.indexOf(prefix) === 0;
        }).forEach(function (tag) {
            orderedEditedTags.push(tag);
        });
    });
    return orderedEditedTags;
}

function expandCollapseAllDryRuns() {
    $(".dry-run-row").each(function (){
       $(this).click();
    });
}

function refreshEditedClusterTags() {
    let originalText = "";
    let newText = "";
    let changedClusters = [];
    Object.keys(allClusters).forEach(function (clusterIdentifier) {
        let originalTags = allClusters[clusterIdentifier].tags;
        let newTags = extractEditedCluster(clusterIdentifier).tags;
        if (JSON.stringify(originalTags) !== JSON.stringify(newTags)) {
            changedClusters.push(clusterIdentifier);
            originalText += clusterIdentifier + ":\n";
            originalTags.forEach(function (tag){
                originalText += "  - " + tag + "\n";
            });
            originalText += "\n";
            newText += clusterIdentifier + ":\n";
            newTags.forEach(function (tag){
                newText += "  - " + tag + "\n";
            });
            newText += "\n";
        }
    });
    $("#tag-edits-diff").html(generateDiffHtml(originalText, newText));
    if (originalText === newText) {
        $(".no-edits-status").show();
        $(".has-edits-status").hide();
    } else {
        $(".no-edits-status").hide();
        $(".has-edits-status").show();
    }
    return changedClusters;
}

function saveClustersChanges() {
    let editedClusterIdentifiers = refreshEditedClusterTags();
    if (editedClusterIdentifiers.length === 0) {
        showOpError("Nothing changes to save");
        return;
    }
    let updateMsg = extractUpdateMessage();
    if (updateMsg.trim() === "") {
        showOpError("Please specify update reason");
        return;
    }
    let clusters = editedClusterIdentifiers.map(function (clusterIdentifier) {
        return extractEditedCluster(clusterIdentifier);
    });
    for (let i = 0; i < clusters.length; i++) {
        let clusterIdentifier = clusters[i].identifier;
        if (enabledClusters.indexOf(clusterIdentifier) > -1) {
            if (JSON.stringify(clusters[i]) !== JSON.stringify(dryRunInspectedClusters[clusterIdentifier])) {
                showOpError("Please dry-run inspect changes");
                return;
            }
        }
    }
    showOpProgress("Saving edited " + clusters.length + " cluster(s)...");
    $
        .ajax("api/clusters/bulk?message=" + encodeURI(updateMsg) + "&" + targetBranchUriParam(), {
            method: "PUT",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(clusters)
        })
        .done(function () {
            showOpSuccess("Successfully edited " + clusters.length + " cluster(s)");
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            showOpError("Bulk edit failed: " + errorMsg);
        });
}

