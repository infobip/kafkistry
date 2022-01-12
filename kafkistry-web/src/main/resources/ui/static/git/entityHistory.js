$(document).ready(function (){
   $(".history-container").each(loadChangesHistory);
});

function loadChangesHistory() {
    let container = $(this);
    let url = container.attr("data-url");
    let historyTable = container.find(".history-table");
    showOpProgressOnId("historyChanges", "Loading history...");
    $
        .ajax(url, {
            method: "GET",
            headers: {ajax: 'true'},
        })
        .done(function (response) {
            hideServerOpOnId("historyChanges");
            historyTable.html(response);
            registerAllInfoTooltips();
            createTextLinks(container);
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideServerOpOnId("historyChanges");
                historyTable.html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpErrorOnId("historyChanges", "Loading changes history failed", errorMsg);
            }
        });
}