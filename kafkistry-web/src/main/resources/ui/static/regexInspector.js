function regexInspectorDispose(textInput) {
    textInput.popover("dispose");
}

function regexInspectorShow(textInput) {
    textInput.popover("show");
}

function regexInspector(textInput, options) {
    textInput.popover("dispose");
    textInput.popover({
        trigger: 'focus',
        placement: 'bottom',
        container: 'body',
        html: true,
        content: function () {
            return generatePopupHtml(textInput.val(), options);
        },
        title: function() {
            return options.title ? options.title : "Regex matches";
        },
    });
    if (!options.disableChangeHook) {
        textInput.on("keypress, input", function () {
            textInput.popover('hide');
            textInput.popover('show');
        });
    } else {
        textInput.popover('show');
    }
}

function generatePopupHtml(inputValue, options) {
    let result;
    try {
        result = processRegex(inputValue, options);
    } catch (e) {
        return '<span class="text-danger">Invalid regex: ' + e + '</span>';
    }
    let html = "";
    html += "<table class='table table-sm small'>";
    html += "<tr class='no-hover'>";
    html += "<th>Hits ("+result.matching.length+")</th>";
    html += "<th>Misses ("+result.filtered.length+")</th>";
    html += "</tr>";
    html += "<tr class='no-hover'>";
    html += "<td>";
    result.matching.forEach(function (value) {
        html += value + "<br/>";
    });
    html += "</td>";
    html += "<td>";
    result.filtered.forEach(function (value) {
        html += value + "<br/>";
    });
    html += "</td>";
    html += "</tr>";
    html += "</table>";
    return html;
}

function processRegex(inputValue, options) {
    let value = options.valuePreProcessor ? options.valuePreProcessor(inputValue) : inputValue;
    let data = options.source;
    if (value.trim().length === 0) {
        return {
            matching: data,
            filtered: []
        };
    }
    let matching = [];
    let filtered = [];
    let regex = options.matchEntire ? RegExp("^" + value + "$") : RegExp(value);
    data.forEach(function (value) {
        if (value.match(regex)) {
            matching.push(value);
        } else {
            filtered.push(value);
        }
    });
    return {
        matching: matching,
        filtered: filtered
    };
}