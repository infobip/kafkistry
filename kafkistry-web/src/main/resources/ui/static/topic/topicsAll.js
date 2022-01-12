$(document).ready(function () {
    $("#clone-existing-btn").click(function (event) {
        event.preventDefault();
        event.stopPropagation();
        $(".clone-existing-container").show();
    });
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
    maybeFilterDatatableByUrlHash();
});
