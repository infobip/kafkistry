$(document).ready(function () {
    adjustFieldsView();
    $(document).on("change", "input[name=fields-display-view-type]", null, adjustFieldsView);
});

function adjustFieldsView() {
    let viewType = $("input[name=fields-display-view-type]:checked").val();
    $("input[name=fields-display-view-type]").each(function () {
        let type = $(this).val();
        if (type === viewType) {
            $(this).closest("label.btn").addClass("active");
        } else {
            $(this).closest("label.btn").removeClass("active");
        }
    });
    let tree = $(".fields-tree");
    let list = $(".fields-list");
    let raw = $(".fields-raw");
    tree.hide();
    list.hide();
    raw.hide();
    switch (viewType) {
        case "tree":
            tree.show();
            break;
        case "list":
            list.show();
            break;
        case "raw":
            raw.show();
            break;
    }
}