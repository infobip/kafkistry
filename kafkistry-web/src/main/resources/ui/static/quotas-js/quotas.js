$(document).ready(function () {
    loadAllQuotaEntitiesTable();
});

function loadAllQuotaEntitiesTable() {
    showOpProgressOnId("allQuotaEntities", "Loading quota entities...");
    whenUrlSchemaReady(function () {
        let url = urlFor("quotas.showEntitiesTable");
        let allQuotaEntitiesResultContainer = $("#all-quota-entities-result");
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnId("allQuotaEntities");
                allQuotaEntitiesResultContainer.html(response);
                refreshAllConfValuesIn(allQuotaEntitiesResultContainer);
                registerAllInfoTooltipsIn(allQuotaEntitiesResultContainer);
                initDatatablesIn(allQuotaEntitiesResultContainer);
                maybeFilterDatatableByUrlHash();
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId("allQuotaEntities");
                    allQuotaEntitiesResultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId("allQuotaEntities", "Failed to get quota entities", errorMsg);
                }
            });
    });
}