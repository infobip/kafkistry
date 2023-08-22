let editor = null;

$(document).ready(function () {
    let textArea = document.getElementById("record-payload-editor");
    editor = CodeMirror.fromTextArea(textArea, {
        lineNumbers: true,
        beautify: {initialBeautify: false, autoBeautify: true},
        mode: {name: 'javascript', json: true},
        extraKeys: {
            "Ctrl-Space": "autocomplete",
            "Ctrl-R": "replace",
            "Ctrl-Enter": dryRunInspectRecordsStructure,
        },
        matchBrackets: true,
        autoCloseBrackets: true,
        highlightSelectionMatches: {minChars: 1, showToken: true, annotateScrollbar: true},
        foldGutter: true,
        gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter", "CodeMirror-lint-markers"]
    });
    $("#format-json-btn").click(formatJson);
    $("#dry-run-inspect-records-structure-btn").click(dryRunInspectRecordsStructure);
    $("select[name=inputMode]").change(adjustInputMode);
    adjustInputMode();
    addExamplesOptions();
});

function adjustInputMode() {
    let inputMode = $("select[name=inputMode]").val();
    if (inputMode === "SINGLE") {
        $("#format-json-btn").closest("div").show();
    } else {
        $("#format-json-btn").closest("div").hide();
    }
}

function formatJson() {
    let jsonStr = editor.getValue();
    let formatted = "";
    try {
        let json = JSON.parse(jsonStr);
        formatted = JSON.stringify(json, null, 2);
    } catch (e) {
        let depth = 0;
        let inString = false;
        for (let i = 0; i < jsonStr.length; i++) {
            let c = jsonStr[i];
            switch (c) {
                case '"':
                    inString = !inString;
                    formatted += c;
                    break;
                case '[':
                case '{':
                    if (inString) {
                        formatted += c;
                    } else {
                        depth++;
                        formatted += c + "\n" + " ".repeat(depth);
                    }
                    break
                case ']':
                case '}':
                    if (inString) {
                        formatted += c;
                    } else {
                        depth = Math.max(0, depth - 1);
                        formatted += "\n" + " ".repeat(depth) + c;
                    }
                    break
                case ',':
                    if (inString) {
                        formatted += c;
                    } else {
                        formatted += c + "\n" + " ".repeat(depth);
                    }
                    break;
                default:
                    formatted += c;
            }
        }
    }
    editor.setValue(formatted);
}

function dryRunInspectRecordsStructure() {
    showOpProgress("Inspecting record structure...");
    let resultContainer = $("#dry-run-analyze-records-result");
    resultContainer.hide();
    let url = urlFor("recordsStructure.showDryRunInspect");
    $
        .ajax(url, {
            method: "POST",
            headers: {ajax: 'true'},
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(extractAnalyzeRecords())
        })
        .done(function (response) {
            hideOpStatus();
            resultContainer.html(response);
            registerAllInfoTooltipsIn(resultContainer)
            resultContainer.show();
            adjustFieldsView();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideOpStatus();
                resultContainer.html(errHtml);
                resultContainer.show();
            } else {
                let errorMsg = extractErrMsg(error);
                showOpError("Got error while trying to dry-run inspect records structure", errorMsg);
            }
        });
}

function extractAnalyzeRecords() {
    let inputMode = $("select[name=inputMode]").val();
    let encoding = $("select[name=encoding]").val();
    let editorContent = editor.getValue();
    let records = [];
    switch (inputMode) {
        case "SINGLE":
            records = [
                {
                    payload: editorContent,
                    headers: {}
                }
            ];
            break;
        case "MULTI":
            records = editorContent.split("\n")
                .map(function (line) {
                    return line.trim();
                })
                .filter(function (line) {
                    return line.length > 0;
                })
                .map(function (line) {
                    return { payload: line, headers: {}};
                });
            break;
    }
    return {
        payloadEncoding: encoding,
        records: records
    };
}

