$(document).ready(function () {
    $("#clone-existing-btn").click(function (event) {
        event.preventDefault();
        event.stopPropagation();
        $(".clone-existing-container").show();
    });

    loadAllTopicsTable();
});

function loadAllTopicsTable() {
    showOpProgressOnId("allTopics", "Loading topics...");
    whenUrlSchemaReady(function () {
        let url = urlFor("topics.showAllTopicsTable");
        let allTopicsResultContainer = $("#all-topics-result");
        $
            .ajax(url, {
                method: "GET",
                headers: {ajax: 'true'},
            })
            .done(function (response) {
                hideServerOpOnId("allTopics");
                allTopicsResultContainer.html(response);
                refreshAllConfValuesIn(allTopicsResultContainer);
                registerAllInfoTooltipsIn(allTopicsResultContainer);
                initDatatablesIn(allTopicsResultContainer);
                setupCloneAutocomplete();
                maybeFilterDatatableByUrlHash();
            })
            .fail(function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    hideServerOpOnId("allTopics");
                    allTopicsResultContainer.html(errHtml);
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpErrorOnId("allTopics", "Failed to get topics", errorMsg);
                }
            });
    });
}

function setupCloneAutocomplete() {
    let allTopics = $(".topicName").get().map(function (value) {
        return $(value).attr("data-topic-name")
    });
    $("#cloneInput")
        .autocomplete({
            source: allTopics,
            select: function () {
                setTimeout(function () {
                    $(".clone-existing-container form").submit();
                }, 10);
            },
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}
