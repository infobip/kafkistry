$(document).ready(function () {
    $("#refresh-git-now-btn").click(doRefreshGitNow);
});

function doRefreshGitNow() {
    showOpProgressOnId("git", "Refreshing git repository...")
    $.post("api/git/refresh-now")
        .done(function () {
            showOpSuccessOnId("git", "Successfully refreshed git, refreshing page...");
            setTimeout(function () {
                location.reload();
            }, 1000);
        })
        .fail(function (error) {
            let errMsg = extractErrMsg(error);
            showOpErrorOnId("git", "Failed to do git refresh", errMsg);
        });
}