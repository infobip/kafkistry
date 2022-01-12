$(document).ready(function () {
    let currentNavItemId = $("meta[name='current-nav']").attr("content");
    highlightCurrentNavItem(currentNavItemId)
});

function highlightCurrentNavItem(currentNavItemId) {
    $(".navbar-nav .nav-item").each(function () {
        $(this).removeClass("active");
    });
    if (currentNavItemId) {
        $("#" + currentNavItemId).addClass("active");
    }
}