let editor = null;

$(document).ready(function () {
    let tableColumns = extractTableColumns();
    let textArea = document.getElementById("sql-edit");
    editor = CodeMirror.fromTextArea(textArea, {
        lineNumbers: true,
        mode: "text/x-sql",
        extraKeys: {
            "Ctrl-Space": "autocomplete",
            "Ctrl-R": "replace",
            "Ctrl-Enter": executeSql,
        },
        hint: CodeMirror.hint.sql,
        hintOptions: {
            tables: tableColumns
        },
        matchBrackets: true,
        autoCloseBrackets: true,
        highlightSelectionMatches: {minChars: 1, showToken: true, annotateScrollbar: true},
        foldGutter: true,
        gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
    });
    $("#execute-sql-btn").click(executeSql);
    $(".query-example").click(insertQueryExample);
    $("#schema-help-btn").click(toggleSchemaHelp);
    $(".sql-insert-text").click(insertTextToEditor);
});

function extractTableColumns() {
    let tableColumns = {};
    $("#table-columns").find(".sql-table").each(function () {
        let tableDiv = $(this);
        let table = tableDiv.attr("data-table");
        let columns = [];
        tableDiv.find(".sql-column").each(function () {
            columns.push($(this).attr("data-column"));
        });
        tableColumns[table] = columns;
    });
    return tableColumns;
}

function insertQueryExample() {
    let example = $(this);
    let sql = example.attr("data-sql");
    editor.setValue("/* " + example.text() + " */\n" + sql);
}

function executeSql() {
    $("#schema-info").hide();
    let autoAddLimit = $("input[name=auto-limit]").is(":checked");
    let sql = editor.getValue();
    if (autoAddLimit) {
        if (sql.toLocaleLowerCase().indexOf("limit") === -1) {
            sql = sql.trim() + "\nLIMIT 20";
            editor.setValue(sql);
        }
    }
    let newUrl = generateNewUrl(sql);
    console.log("New url: " + newUrl);
    window.history.replaceState(null, null, newUrl);
    showOpProgress("Executing sql query...");
    $
        .ajax(urlFor("sql.showSqlQueryResult"), {
            method: "POST",
            headers: {ajax: 'true'},
            data: {query: sql}
        })
        .done(function (response) {
            hideOpStatus();
            $("#sql-result").html(response);
            $("#chart-components-wrapper").show();
            setupChart();
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            if (errHtml) {
                hideOpStatus();
                $("#sql-result").html(errHtml);
            } else {
                let errorMsg = extractErrMsg(error);
                showOpError("SQL execution failed", errorMsg);
            }
        });
}

function generateNewUrl(sql) {
    let newUrlParams = "?query=" + encodeURIComponent(sql);
    let currentUrl = window.location.href;
    let paramsStart = currentUrl.indexOf("?");
    let newUrl;
    if (paramsStart === -1) {
        newUrl = currentUrl + newUrlParams;
    } else {
        newUrl = currentUrl.substring(0, paramsStart) + newUrlParams
    }
    return newUrl;
}

function toggleSchemaHelp() {
    let schemaInfo = $("#schema-info");
    if (schemaInfo.is(":visible")) {
        schemaInfo.hide();
    } else {
        schemaInfo.show();
    }
}

function insertTextToEditor() {
    let element = $(this);
    let text = element.text().trim();
    if (editor.getValue().trim() === "") {
        if (element.hasClass("sql-table")) {
            editor.setValue("SELECT *\n" + "FROM " + text + " AS t");
        } else if (element.hasClass("sql-column")) {
            let table = element.closest("tr").find(".sql-table").text();
            editor.setValue("SELECT t." + text + "\nFROM " + table + " AS t");
        }
    } else {
        editor.replaceSelection(text);
    }
    editor.focus();
}
