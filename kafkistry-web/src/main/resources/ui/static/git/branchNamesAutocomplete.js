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
            source: function(request, response) {
                const filtered = filterAutocompleteSuggestions(branches, request.term);
                response(filtered);
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}
