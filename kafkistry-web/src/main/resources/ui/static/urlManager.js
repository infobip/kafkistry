let urlSchema = null;
let readyListeners = [];

$(document).ready(function () {
    $.ajax("api/static/url-schema")
        .done(function (urlSchemaResponse) {
            urlSchema = urlSchemaResponse;
            readyListeners.forEach(function (callback) {
                callback();
            });
        });
});

function whenUrlSchemaReady(callback) {
    if (urlSchema) {
        callback();
    } else {
        readyListeners.push(callback);
    }
}

function urlFor(target, parameters) {
    let urlSpec = urlSchema[target];
    if (!urlSpec) {
        throw"Url spec for '" + target + "' is not found";
    }
    let path = urlSpec.path;
    let params = "";
    if (parameters) {
        Object.keys(parameters).forEach(function (key, index) {
            let val = parameters[key];
            if (val != null) {
                let replace = "{"+key+"}";
                if (path.indexOf(replace) >= 0) {
                    path = path.replace(replace, encodeURI(val));
                } else {
                    params += key + "=" + encodeURI(val) + "&";
                }
            }
        });
    }
    if (params.length > 0) {
        return path + "?" + params;
    }
    return path;
}
