$(document).ready(function () {
    loadAllPrincipalsTable();
});

function loadAllPrincipalsTable() {
    showOpProgressOnId("allPrincipals", "Loading principals...");
    whenUrlSchemaReady(function () {
        let url = urlFor("acls.showPrincipalsTable");
        let allPrincipalsResultContainer = $("#all-principals-result");
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnId("allPrincipals");
                allPrincipalsResultContainer.html(response);
                refreshAllConfValuesIn(allPrincipalsResultContainer);
                registerAllInfoTooltipsIn(allPrincipalsResultContainer);
                initDatatablesIn(allPrincipalsResultContainer);
                maybeFilterDatatableByUrlHash();
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId("allPrincipals");
                    allPrincipalsResultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId("allPrincipals", "Failed to get principals", errorMsg);
                }
            });
    });
}