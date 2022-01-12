$(document).ready(function () {
    $(".cancel-btn").click(function () {
        window.history.go(-getBackDepth());
    });
});

function getBackDepth() {
    let backStr = getParameterByName("back");
    if (backStr) {
        return parseInt(backStr);
    } else {
        return 1;
    }
}

function getParameterByName(name, url) {
    if (!url) url = window.location.href;
    name = name.replace(/[\[\]]/g, '\\$&');
    let regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, ' '));
}