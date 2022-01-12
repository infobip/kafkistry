$(document).ready(function () {
    createTextLinks();
});

function createTextLinks(container) {
    if (!container) {
        container = $(document);
    }
    container.find(".text-links").each(function () {
        createLinksInText(this);
    });
}