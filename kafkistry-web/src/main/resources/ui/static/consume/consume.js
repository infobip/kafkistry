$(document).ready(function () {
    registerAllInfoTooltips();
    $("#consume-btn").click(startConsume);
    $(".consume-stop-btn").click(stopConsume);
    allClusterTopics = $(".cluster-topics").map(function () {
        let clusterData = $(this);
        let clusterIdentifier = clusterData.attr("data-cluster-identifier");
        let topicNames = clusterData.find(".data-topic-name").map(function () {
            return $(this).attr("data-topic");
        }).get();
        return {clusterIdentifier: clusterIdentifier, topicNames: topicNames};
    }).get().reduce(function (map, entry) {
        map[entry.clusterIdentifier] = entry.topicNames;
        return map;
    }, {});
    adjustTopicNamesDropdown();
    updateJsonFieldsAutocomplete();
    let messagesContainer = $("#messages-container");
    messagesContainer.on("click", "#continue-consume-btn", null, startContinueConsume);
    messagesContainer.on("click", "#expand-all-btn", null, expandAll);
    messagesContainer.on("click", "#collapse-all-btn", null, collapseAll);
    messagesContainer.on("click", "input[name=showMetadata]", null, adjustFlagsVisibility);
    messagesContainer.on("click", "input[name=showKey]", null, adjustFlagsVisibility);
    messagesContainer.on("click", "input[name=showHeaders]", null, adjustFlagsVisibility);
    messagesContainer.on("click", "input[name=showValue]", null, adjustFlagsVisibility);
    messagesContainer.on("click", "#export-btn", null, function () {
        let content = generateExportContent();
        let fileName = exportFileName();
        saveAsFile(content, fileName, "text/plain;charset=utf-8");
    });
    messagesContainer.on("click", ".headers-expand", null, headersExpand);
    messagesContainer.on("click", ".headers-collapse", null, headersCollapse);
    let offsetTypeInput = $("select[name=offsetType]");
    if (offsetTypeInput.val() === 'TIMESTAMP') {
        initDatePicker();
    }
    let clusterPicker = $("select[name=cluster]");
    clusterPicker.change(adjustClusterChange);
    tweakSelectPickerBootstrapStyling(clusterPicker);
    let topicInput = $("input[name=topic]");
    topicInput.on('autocompleteselect', function () {
        setTimeout(adjustTopicChange, 10);
    });
    topicInput.change(adjustTopicChange);
    offsetTypeInput.change(adjustDatePicker);
    updatePickedTimeIndicator();
    setupInspectTopicButton();
    $(document).on("click", ".kafka-value-copy-btn", null, copyKafkaValueToClipboard);
    $(document).on("click", ".kafka-value-toggle-suppressed-btn", null, toggleSuppressedKafkaValues);
});

let allClusterTopics = {};
let prevOffsetVal = null;
let preferExpandAll = true;

function setupInspectTopicButton() {
    let formData = readFormData();
    let inspectBtn = $("#inspect-topic-btn");
    if (formData.clusterIdentifier && formData.topicName) {
        whenUrlSchemaReady(function () {
            let inspectUrl = urlFor("topics.showInspectTopicOnCluster", {
                topicName: formData.topicName,
                clusterIdentifier: formData.clusterIdentifier
            });
            inspectBtn.attr("href", inspectUrl);
            inspectBtn.show();
        });
    } else {
        inspectBtn.hide();
    }
}

function adjustClusterChange() {
    adjustTopicNamesDropdown();
    updateJsonFieldsAutocomplete();
    setupInspectTopicButton();
}

function adjustTopicChange() {
    updateJsonFieldsAutocomplete();
    setupInspectTopicButton();
}

function adjustTopicNamesDropdown() {
    let cluster = readFormData().clusterIdentifier;
    let topics = allClusterTopics[cluster] ? allClusterTopics[cluster] : [];
    $("input[name=topic]")
        .autocomplete({
            source: topics,
            minLength: 0
        })
        .focus(function () {
            $(this).data("uiAutocomplete").search($(this).val());
        });
}

function initDatePicker() {
    $.datetimepicker.setDateFormatter("moment");
    let minutesStep = 10;
    let offsetInput = $("input[name=offset]");
    let currentVal = parseInt(offsetInput.val());
    //use now time if timestamp is more than year difference from now
    if (Math.abs(currentVal - Date.now()) > 1000 * 3600 * 24 * 365) {
        prevOffsetVal = currentVal;
        offsetInput.val(Date.now().toString());
    }
    let timestampPicked = function (currTime, picker) {
        let currTimestampMillis = currTime.getTime();
        let roundedTimeMillis = currTimestampMillis - (currTimestampMillis % (1000 * 60 * minutesStep));
        picker.val(roundedTimeMillis.toString());
    };
    offsetInput.datetimepicker({
        format: 'x',
        step: minutesStep,
        onSelectTime: timestampPicked,
        onSelectDate: timestampPicked,
    });
    offsetInput.change(updatePickedTimeIndicator);
    updatePickedTimeIndicator();
}

function adjustDatePicker() {
    let offsetInput = $("input[name=offset]");
    if (readFormData().readConfig.fromOffset.type === 'TIMESTAMP') {
        initDatePicker();
        offsetInput.datetimepicker("show");
    } else {
        if (prevOffsetVal !== null) {
            offsetInput.val(prevOffsetVal);
            prevOffsetVal = null;
        }
        offsetInput.datetimepicker("destroy");
    }
}

function updatePickedTimeIndicator() {
    let timeIndicator = $("#picked-time-indicator");
    if (readFormData().readConfig.fromOffset.type === 'TIMESTAMP') {
        let timestampInput = $("input[name=offset]");
        timeIndicator.attr("data-time", timestampInput.val());
        formatTimestampIn($(".offset-type-form"));
        timeIndicator.show();
    } else {
        timeIndicator.hide();
    }
}

function startConsume() {
    let formData = readFormData();
    doConsume(formData, {trigger: "NONE"});
}

function stopConsume() {
    consumeWasAborted = true;
    if (consumeXHR) {
        consumeXHR.abort();
    }
}

function startContinueConsume() {
    doStartContinueConsume({trigger: "MANUAL"});
}

function doStartContinueConsume(continuationCtx) {
    let formData = readFormData();
    let partitionFromOffset = {};
    $(".partition-read-status").each(function () {
        let partitionStatusRow = $(this);
        let partition = parseInt(partitionStatusRow.attr("data-partition"));
        partitionFromOffset[partition] = parseInt(partitionStatusRow.attr("data-ended-at-offset"));
    })
    formData.readConfig.partitionFromOffset = partitionFromOffset;
    doConsume(formData, continuationCtx);
}

let consumeWasAborted = false;
let consumeXHR = null;

function doConsume(formData, continuationCtx) {
    let newUrl = generateNewUrl(formData);
    console.log("New url: " + newUrl);
    window.history.replaceState(null, null, newUrl);
    let readingMessage;
    switch (continuationCtx.trigger) {
        case "AUTO":
            readingMessage = `Auto-continue reading messages (iteration: ${continuationCtx.iteration})...`;
            break;
        case "MANUAL":
            readingMessage = `Continue reading messages...`;
            break;
        default:
            readingMessage = "Reading messages...";
    }
    showOpProgress(readingMessage);
    let consumeTriggerButtons = function (){
        return $(".consume-trigger-btn");
    }
    let consumeStopButtons = function (){
        return $(".consume-stop-btn");
    }
    consumeTriggerButtons().hide();
    consumeStopButtons().show();
    let showFlags = currentShowFlags();
    let errorContainer = $("#error-container");
    let consumeResultContainer = $("#messages-container");
    startTicking(formData.readConfig.maxWaitMs / 1000, "server-op-status", readingMessage);
    let url;
    let postData;
    if (continuationCtx.trigger !== "NONE") {
        url = "consume/read-topic/continue";
        postData = {
            readConfig: formData.readConfig,
            previousPartitions: consumeStatusData().partitionStats,
        };
    } else {
        url = "consume/read-topic";
        postData = formData.readConfig;
    }
    consumeWasAborted = false;
    consumeXHR = $
        .ajax(url +
            "?topicName=" + encodeURI(formData.topicName) +
            "&clusterIdentifier=" + encodeURI(formData.clusterIdentifier), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(postData),
        })
        .always(function () {
            errorContainer.hide();
            stopTicking();
            consumeTriggerButtons().show();
            consumeStopButtons().hide();
            consumeXHR = null;
        })
        .done(function (response) {
            hideOpStatus();
            let partitionStatsExpanded = $("#partition-stats-container").hasClass("show");
            consumeResultContainer.hide();
            consumeResultContainer.html(response);
            if (partitionStatsExpanded) {
                $("#partition-stats-toggler").click();
            }
            setShowFlags(showFlags);
            adjustFlagsVisibility();
            consumeResultContainer.show();
            formatTimestamp();
            renderValues();
            if (preferExpandAll) {
                expandAll();
            } else {
                collapseAll();
            }
            maybeAutoContinue(continuationCtx);
        })
        .fail(function (error) {
            let errHtml = extractErrHtml(error);
            let mayContinue;
            if (errHtml) {
                errorContainer.html(errHtml);
                errorContainer.show();
                mayContinue = true;
            } else {
                let errorMsg = extractErrMsg(error);
                showOpError("Topic reading request failed:", errorMsg);
                mayContinue = !consumeWasAborted;
            }
            if (mayContinue) {
                setTimeout(function () {
                    //don't re-continue right away
                    hideOpStatus();
                    maybeAutoContinue(continuationCtx);
                }, 1000);
            }
        });
}

function maybeAutoContinue(continuationCtx) {
    let formData= readFormData();
    if (!formData.readConfig.autoContinuation.enabled) {
        return; //auto-continuation not enabled
    }
    let consumeStats = consumeStatusData();
    if (consumeStats.resultCount > 0) {
        return; //got records, no more continuations
    }
    if (consumeStats.reachedEnd && !formData.readConfig.autoContinuation.enabledAfterEnd) {
        return; //stop continuation, end is reached
    }
    if (!continuationCtx || continuationCtx.trigger !== "AUTO") {
        continuationCtx = {trigger: "AUTO", iteration: 1};
    } else {
        continuationCtx.iteration++;
    }
    doStartContinueConsume(continuationCtx);
}

function consumeStatusData() {
    let container = $("#consume-status-data");
    let partitionStats = parseJsonOrNull(container.attr("data-partitionStats"));
    return {
        readCount: parseIntOrDefault(container.attr("data-readCount"), 0),
        resultCount: parseIntOrDefault(container.attr("data-resultCount"), 0),
        timedOut: parseBooleanOrUndefined(container.attr("data-timedOut")),
        reachedEnd: parseBooleanOrUndefined(container.attr("data-reachedEnd")),
        partitionStats: partitionStats ? partitionStats : {},
    }
}

function readFormData() {
    return {
        topicName: $("input[name=topic]").val(),
        clusterIdentifier: $("select[name=cluster]").val(),
        readConfig: {
            numRecords: parseInt($("input[name=numRecords]").val()),
            partitions: parseIntListOrUndefined($("input[name=partitions]").val()),
            notPartitions: parseIntListOrUndefined($("input[name=notPartitions]").val()),
            maxWaitMs: parseInt($("input[name=maxWaitMs]").val()),
            waitStrategy: $("select[name=waitStrategy]").val(),
            fromOffset: {
                type: $("select[name=offsetType]").val(),
                offset: parseInt($("input[name=offset]").val())
            },
            readOnlyCommitted: $("input[name=readOnlyCommitted]").is(":checked"),
            recordDeserialization: {
                keyType: deserializationType($("select[name=keyDeserializerType]").val()),
                valueType: deserializationType($("select[name=valueDeserializerType]").val()),
                headersType: deserializationType($("select[name=headersDeserializerType]").val()),
            },
            readFilter: readFilterData(),
            autoContinuation: {
                enabled: $("input[name=auto-continuation]").is(":checked"),
                enabledAfterEnd: $("input[name=auto-continuation-after-end]").is(":checked"),
            },
        }
    }
}

function deserializationType(typeName) {
    return typeName === "AUTO" ? null : typeName;
}

function exportFileName() {
    let formData = readFormData();
    return "messages_" +
        formData.clusterIdentifier + "_" +
        formData.topicName + ".json"
}

function generateNewUrl(formData) {
    let newUrlParams = "" +
        "?clusterIdentifier=" + encodeURIComponent(formData.clusterIdentifier) +
        "&topicName=" + encodeURIComponent(formData.topicName) +
        "&numRecords=" + formData.readConfig.numRecords +
        "&partitions=" + (formData.readConfig.partitions === undefined ? "" : formData.readConfig.partitions.join(",")) +
        "&notPartitions=" + (formData.readConfig.notPartitions === undefined ? "" : formData.readConfig.notPartitions.join(",")) +
        "&maxWaitMs=" + formData.readConfig.maxWaitMs +
        "&waitStrategy=" + formData.readConfig.waitStrategy +
        "&offsetType=" + formData.readConfig.fromOffset.type +
        "&offset=" + formData.readConfig.fromOffset.offset +
        "&keyDeserializerType=" + (formData.readConfig.recordDeserialization.keyType || "") +
        "&valueDeserializerType=" + (formData.readConfig.recordDeserialization.valueType || "") +
        "&headersDeserializerType=" + (formData.readConfig.recordDeserialization.headersType || "") +
        "&readFilterJson=" + encodeURIComponent(JSON.stringify(formData.readConfig.readFilter)) +
        "&readOnlyCommitted=" + formData.readConfig.readOnlyCommitted +
        "&autoContinuation=" + formData.readConfig.autoContinuation.enabled +
        "&autoContinuationAfterEnd=" + formData.readConfig.autoContinuation.enabledAfterEnd;
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

function expandAll() {
    preferExpandAll = true;
    expandAllIn($(document));
}

function expandAllIn(container) {
    container.find(".headers-collapsed").hide();
    container.find(".headers-expanded").show();
    let numElements = 0;
    while (true) {
        let elements = container.find("a.disclosure:contains('⊕')");
        if (elements.length > numElements) {
            elements.click();
            numElements = elements.length;
        } else {
            break;
        }
    }
}

function collapseAll() {
    preferExpandAll = false;
    collapseAllIn($(document));
}

function collapseAllIn(container) {
    container.find("a.disclosure:contains('⊖')").click();
    container.find(".headers-collapsed").show();
    container.find(".headers-expanded").hide();
}

function headersExpand() {
    let headers = $(this).closest(".headers-row");
    headers.find(".headers-collapsed").hide();
    headers.find(".headers-expanded").show();
}

function headersCollapse() {
    let headers = $(this).closest(".headers-row");
    headers.find(".headers-expanded").hide();
    headers.find(".headers-collapsed").show();
}

function adjustFlagsVisibility() {
    let flagName = $(this).attr("name");
    setRecordsVisibility(currentShowFlags(), flagName);
}

function currentShowFlags() {
    let initial = $(".show-flags").length === 0;
    return {
        metadata: initial ? true : $("input[name=showMetadata]").is(":checked"),
        key: initial ? true : $("input[name=showKey]").is(":checked"),
        headers: initial ? true : $("input[name=showHeaders]").is(":checked"),
        value: initial ? true : $("input[name=showValue]").is(":checked"),
    }
}

function setShowFlags(showFlags) {
    $("input[name=showMetadata]").prop("checked", showFlags.metadata);
    $("input[name=showKey]").prop("checked", showFlags.key);
    $("input[name=showHeaders]").prop("checked", showFlags.headers);
    $("input[name=showValue]").prop("checked", showFlags.value);
}

function setRecordsVisibility(showFlags, flagName) {
    function adjust(show, elems) {
        show ? elems.show() : elems.hide();
    }
    if (!flagName || flagName === "showMetadata") {
        adjust(showFlags.metadata, $(".metadata-row"));
    }
    if (!flagName || flagName === "showKey") {
        adjust(showFlags.key, $(".key-row"));
    }
    if (!flagName || flagName === "showHeaders"){
        adjust(showFlags.headers, $(".headers-row"));
    }
    if (!flagName || flagName === "showValue") {
        adjust(showFlags.value, $(".value-row"));
    }
}

function htmlDecode(input) {
    let doc = new DOMParser().parseFromString(input, "text/html");
    return doc.documentElement.textContent;
}

function renderValues() {
    $("div.value-json[data-json]").each(function () {
        let div = $(this);
        let dataJsonAttr = div.attr("data-json");
        let json = decodeURIComponent(dataJsonAttr);
        let object = deserializeJson(json);
        div.append($(renderjson(object)));
        div.hide();
        expandAllIn(div);
        div.find("span.string").get()
            .filter(function (elem) {
                return $(elem).text() === '"***MASKED***"';
            })
            .forEach(function (elem) {
                $(elem).removeClass("string").addClass("masked").attr("title", "sensitive data");
            });
        collapseAllIn(div);
        div.show();
    });

    $("div.value-string[data-string]").each(function () {
        let div = $(this);
        let dataStringAttr = div.attr("data-string");
        let string = decodeURIComponent(dataStringAttr);
        div.append(pre(string));
    });
    $("div.value-base64[data-base64]").each(function () {
        let div = $(this);
        let base64 = htmlDecode(div.attr("data-base64"));
        div.append(pre(base64));
    });
}

function deserializeJson(json) {
    try {
        return JsonParseBigInt(json);
    } catch (e) {
        return JsonParseBigInt(json.replace(/\bNaN\b/g, '"NaN"'));
    }
}

function pre(text) {
    return "<pre class='renderjson'>" + $("<span>").text(text).html() + "</pre>";
}

function saveAsFile(text, fileName, mimeContentType) {
    try {
        let b = new Blob([text], {type: mimeContentType});
        saveAs(b, fileName);
    } catch (e) {
        window.open("data:" + mimeContentType + "," + encodeURIComponent(text), '_blank', '');
    }
}

function generateExportContent() {
    function kafkaValue(valueContainer) {
        let base64 = valueContainer.attr("data-base64");
        let deserializations = valueContainer.find(".record-value").map(function () {
            let valueHolder = $(this);
            let asJson = parseJsonOrNull(decodeUrlOrNull(valueHolder.attr("data-json")));
            let asString = decodeUrlOrNull(valueHolder.attr("data-string"));
            let typeTag = valueHolder.attr("data-type");
            let value;
            switch (typeTag) {
                case "NULL":
                    value = null;
                    break;
                case "EMPTY":
                    value = "";
                    break;
                case "BYTES":
                    value = base64;
                    break;
                default:
                    value = asJson != null ? asJson : asString;
            }
            return {
                type: typeTag,
                value: value,
            }
        }).get();
        return {
            rawBase64: base64,
            deserializations: deserializations,
        };
    }
    let data = $(".record")
        .map(function () {
            let record = $(this);
            return {
                topic: record.find(".topic").text(),
                partition: parseInt(record.find(".partition").text()),
                offset: parseInt(record.find(".offset").text()),
                leaderEpoch: parseIntOrNull(record.find(".leader-epoch").text()),
                key: kafkaValue(record.find(".key-row .kafka-value-container")),
                timestamp: parseInt(record.find(".timestamp").attr("data-timestamp")),
                timestampType: record.find(".record-timestamp-type").attr("data-timestamp-type"),
                headers: record.find(".record-header").map(function () {
                    let header = $(this);
                    return {
                        key: header.find(".key").text(),
                        value: kafkaValue(header.find(".kafka-value-container"))
                    }
                }).get(),
                value: kafkaValue(record.find(".value-row .kafka-value-container")),
            }
        }).get();
    return JSON.stringify(data, null, 4);
}

function parseIntListOrUndefined(numbersText) {
    let numbers = numbersText.split(",")
        .map(function (numberText) {
            return parseIntOrUndefined(numberText.trim());
        })
        .filter(function (number) {
            return number !== undefined;
        });
    if (numbers.length === 0) {
        return undefined;
    }
    return numbers;
}

function parseIntOrUndefined(numberText) {
    return parseIntOrDefault(numberText, undefined);
}

function parseIntOrNull(numberText) {
    return parseIntOrDefault(numberText, null);
}

function parseIntOrDefault(numberText, defaultValue) {
    let result = parseInt(numberText);
    if (isNaN(result)) {
        return defaultValue;
    }
    return result;
}

function parseBooleanOrUndefined(boolText) {
    return parseBooleanOrDefault(boolText, undefined);
}

function parseBooleanOrDefault(boolText, defaultValue) {
    if (typeof boolText === "string") {
        let sanitized = boolText.trim().toLowerCase();
        if (sanitized === "true") {
            return true;
        } else if (sanitized === "false") {
            return false;
        } else {
            return defaultValue;
        }
    } else if (typeof boolText === "boolean") {
        return boolText;
    } else {
        return defaultValue;
    }
}

function decodeUrlOrNull(string) {
    if (string === undefined) {
        return undefined;
    }
    if (string === null) {
        return null;
    }
    return decodeURIComponent(string);
}

function parseJsonOrNull(json) {
    if (json === undefined) {
        return undefined;
    }
    if (json === null) {
        return null;
    }
    return deserializeJson(json);
}

function copyKafkaValueToClipboard() {
    let copyBtn = $(this);
    let gutter = copyBtn.closest(".kafka-value-gutter");
    let copyInput = gutter.find("input[name=kafkaCopyValue]");
    if (navigator.clipboard !== undefined) {
        navigator.clipboard.writeText(copyInput.val()).then(function() {
            showCopiedTooltip(copyBtn, copyInput);
        }, function() {
            alert("failed to copy");
        });
    } else {
        console.log("Copy to clipboard using legacy document.execCommand('copy')");
        copyInput.show();
        copyInput.select();
        document.execCommand('copy');
        copyInput.hide();
        showCopiedTooltip(copyBtn, copyInput);
    }
}

function toggleSuppressedKafkaValues() {
    let showBtn = $(this);
    let valueContainer = showBtn.closest(".kafka-value-container");
    valueContainer.find(".suppresed").toggle();
}

function showCopiedTooltip(copyBtn, copyInput) {
    console.log("copied: '"+copyInput.val()+"'");
    let originalTitle = copyBtn.attr("title");
    copyBtn.attr("data-bs-placement", "auto");
    copyBtn.attr("data-bs-trigger", "manual");
    copyBtn.attr("title", "Copied!");
    copyBtn.tooltip('show');
    setTimeout(function () {
        copyBtn.tooltip('dispose');
        copyBtn.attr("title", originalTitle);
    }, 2000);
}

