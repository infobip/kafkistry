$(document).ready(function () {
    if ($("input[name=targetBranch]").length > 0) {
        fetchBranchNames();
    }
});

function fetchBranchNames() {
    $
        .ajax("api/git/branches", {
            method: "GET"
        })
        .done(function (branches) {
            initBranchNamesAutocomplete(branches);
        });
}

function initBranchNamesAutocomplete(branches) {
    $("input[name=targetBranch]")
        .autocomplete({
            source: branches,
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}
