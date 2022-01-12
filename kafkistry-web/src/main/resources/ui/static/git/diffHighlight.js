$(document).ready(function () {
    displayFilesDiffs();
});

function displayFilesDiffs() {
    $(".file-content").each(function () {
        let fileContent = $(this);
        let before = fileContent.find(".before").text();
        let after = fileContent.find(".after").text();
        let diffHtml = generateDiffHtml(before, after);
        fileContent.find(".diff").html(diffHtml);
    });
}