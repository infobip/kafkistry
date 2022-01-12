$(document).ready(function () {
    $(".rebalance-nav-item").click(function () {
        let link = $(this).attr("data-href");
        location.href = link + "&back=" + (getBackDepth() + 1);
    });
});
