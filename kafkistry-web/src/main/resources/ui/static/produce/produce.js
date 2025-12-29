let valueEditor = null;

$(document).ready(function() {
    let headerCount = 0;
    let metadataUpdateTimeout = null;

    let clusterSelect = $("#cluster-select");
    let nullKeyCheckbox = $("#null-key-checkbox");
    let nullValueCheckbox = $("#null-value-checkbox");
    let topicInput = $("#topic-input");
    let produceBtn = $("#produce-btn");
    const resultContainer = $("#result-container");

    initCodeMirror();
    initTopicAutocomplete();

    // Populate initial sample data if available
    if (initialSampleData && (initialSampleData.key || initialSampleData.value || initialSampleData.headers.length > 0)) {
        populateSampleData(initialSampleData);
    }

    clusterSelect.on("change", function() {
        updateTopicAutocomplete();
        updateTopicMetadata();
    });

    nullKeyCheckbox.on("change", function() {
        if (this.checked) {
            $("#key-section").hide();
        } else {
            $("#key-section").show();
        }
    });

    nullValueCheckbox.on("change", function() {
        if (this.checked) {
            $("#value-section").hide();
        } else {
            $("#value-section").show();
        }
    });

    $("#value-serializer").on("change", function() {
        updateCodeMirrorMode($(this).val());
    });

    $("#add-header-btn").on("click", function() {
        addHeaderRow();
    });

    produceBtn.on("click", function(e) {
        e.preventDefault();
        sendRecord();
    });

    function initCodeMirror() {
        let textArea = document.getElementById("value-value");
        let initialSerializer = $("#value-serializer").val();
        valueEditor = CodeMirror.fromTextArea(textArea, {
            lineNumbers: true,
            mode: initialSerializer === "JSON" ? {name: 'javascript', json: true} : "text/plain",
            extraKeys: {
                "Ctrl-Space": "autocomplete",
                "Ctrl-R": "replace",
                "Ctrl-Enter": function() {
                    sendRecord();
                }
            },
            matchBrackets: true,
            autoCloseBrackets: true,
            highlightSelectionMatches: {minChars: 1, showToken: true, annotateScrollbar: true},
            foldGutter: true,
            gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
        });
    }

    function updateCodeMirrorMode(serializerType) {
        if (serializerType === "JSON") {
            valueEditor.setOption("mode", {name: 'javascript', json: true});
        } else {
            valueEditor.setOption("mode", "text/plain");
        }
    }

    function initTopicAutocomplete() {
        topicInput.autocomplete({
            source: function(request, response) {
                const cluster = clusterSelect.val();
                if (!cluster) {
                    response([]);
                    return;
                }
                const topics = clusterTopics[cluster] || [];
                const filtered = topics.filter(t =>
                    t.toLowerCase().includes(request.term.toLowerCase())
                );
                response(filtered);
            },
            minLength: 0,
            select: function(event, ui) {
                // Update serializers and sample data when a topic is selected from autocomplete
                setTimeout(function() {
                    updateTopicMetadata();
                }, 100);
            }
        });
        topicInput.on("focus", function() {
            $(this).autocomplete("search", "");
        });
    }

    function updateTopicAutocomplete() {
        topicInput.val("");
    }

    topicInput.on("change", function() {
        updateTopicMetadata();
    });

    function updateTopicMetadata() {
        const cluster = clusterSelect.val();
        const topic = topicInput.val();

        if (!cluster || !topic) {
            $("#partition-info").text("");
            return;
        }

        // Debounce to prevent multiple simultaneous API calls
        if (metadataUpdateTimeout) {
            clearTimeout(metadataUpdateTimeout);
        }

        metadataUpdateTimeout = setTimeout(function() {
            $.ajax({
                url: "produce/topic-metadata?clusterIdentifier=" + encodeURIComponent(cluster) + "&topicName=" + encodeURIComponent(topic),
                method: "GET",
                success: function(data) {
                    // Update default serializers
                    if (data.defaultKeySerializer) {
                        $("#key-serializer").val(data.defaultKeySerializer);
                    }
                    if (data.defaultValueSerializer) {
                        $("#value-serializer").val(data.defaultValueSerializer);
                        updateCodeMirrorMode(data.defaultValueSerializer);
                    }

                    // Update partition info
                    if (data.partitionCount != null && data.partitionCount > 0) {
                        $("#partition-info").text(" (valid range: 0 - " + (data.partitionCount - 1) + ")");
                    } else {
                        $("#partition-info").text("");
                    }

                    // Update sample data
                    populateSampleData(data);
                },
                error: function(xhr) {
                    // Silently fail - keep current values
                    $("#partition-info").text("");
                    console.log("Could not fetch topic metadata:", xhr.responseText);
                }
            });
        }, 100); // 100ms debounce
    }

    function populateSampleData(sampleData) {
        if (!sampleData) {
            sampleData = {};
        }

        // Populate key if available
        if (sampleData.sampleKey) {
            $("#key-value").val(sampleData.sampleKey);
        } else {
            $("#key-value").val('');
        }

        // Populate value if available
        if (sampleData.sampleValue) {
            valueEditor.setValue(sampleData.sampleValue);
        } else {
            valueEditor.setValue('');
        }

        // Clear existing headers
        $(".header-row").remove();

        // Populate headers if available
        if (sampleData.sampleHeaders) {
            // Add sample headers
            sampleData.sampleHeaders.forEach(function(header) {
                addHeaderRow(header.name, header.value);
            });
        }
    }

    function addHeaderRow(headerName, headerValue) {
        headerCount++;
        const headerRow = $(`
            <div class="header-row" data-header-id="${headerCount}">
                <div class="row">
                    <div class="col-md-4">
                        <input type="text" class="form-control form-control-sm header-key"
                               placeholder="Header key" value="${headerName || ''}">
                    </div>
                    <div class="col-auto">
                        <label class="btn btn-sm btn-outline-secondary" title="Set value of header to null">
                            <input type="checkbox" class="header-null-value-checkbox"> Null
                        </label>
                    </div>
                    <div class="col header-value-section">
                        <div class="row">
                            <div class="col-md-auto">
                                <select class="form-control form-control-sm header-serializer">
                                    ${getSerializerOptions(defaultHeaderSerializer)}
                                </select>
                            </div>
                            <div class="col-md">
                                <input type="text" class="form-control form-control-sm header-value"
                                       placeholder="Header value" value="${headerValue || ''}">
                            </div>
                        </div>
                    </div>
                    <div class="col-auto ml-auto">
                        <button type="button" class="btn btn-sm btn-danger remove-header-btn">x</button>
                    </div>
                </div>
            </div>
        `);

        headerRow.find(".header-null-value-checkbox").on("change", function() {
            if (this.checked) {
                headerRow.find(".header-value-section").hide();
            } else {
                headerRow.find(".header-value-section").show();
            }
        });

        headerRow.find(".remove-header-btn").on("click", function() {
            headerRow.remove();
        });

        $("#headers-container").append(headerRow);
    }

    function getSerializerOptions(defaultValue) {
        let options = "";
        $("#key-serializer option").each(function() {
            const value = $(this).val();
            const text = $(this).text();
            const selected = (defaultValue && value === defaultValue) ? " selected" : "";
            options += `<option value="${value}"${selected}>${text}</option>`;
        });
        return options;
    }

    function buildProduceRequest() {
        const request = {
            key: null,
            value: null,
            headers: [],
            partition: null,
            timestamp: null
        };

        if (!nullKeyCheckbox.is(":checked")) {
            request.key = {
                content: $("#key-value").val(),
                serializerType: $("#key-serializer").val()
            };
        }

        if (!nullValueCheckbox.is(":checked")) {
            request.value = {
                content: valueEditor.getValue(),
                serializerType: $("#value-serializer").val()
            };
        }

        $(".header-row").each(function() {
            const key = $(this).find(".header-key").val();
            const nullValueChecked = $(this).find(".header-null-value-checkbox").is(":checked");

            if (key) {
                let headerValue = null;
                if (!nullValueChecked) {
                    const value = $(this).find(".header-value").val();
                    const serializer = $(this).find(".header-serializer").val();
                    headerValue = {
                        content: value,
                        serializerType: serializer
                    };
                }
                request.headers.push({
                    key: key,
                    value: headerValue,
                });
            }
        });

        const partition = $("#partition-input").val();
        if (partition) {
            request.partition = parseInt(partition);
        }

        const timestamp = $("#timestamp-input").val();
        if (timestamp) {
            request.timestamp = parseInt(timestamp);
        }

        return request;
    }

    function sendRecord() {
        const cluster = clusterSelect.val();
        const topic = topicInput.val();

        if (!cluster || !topic) {
            showError("Please select both cluster and topic");
            return;
        }

        const request = buildProduceRequest();

        produceBtn.prop("disabled", true);
        showOpProgress("Producing...")

        $.ajax({
            url: "produce/send-record?clusterIdentifier=" + encodeURIComponent(cluster) + "&topicName=" + encodeURIComponent(topic),
            method: "POST",
            data: JSON.stringify(request),
            contentType: "application/json",
            headers: {ajax: 'true'},
            success: function(result) {
                displayResult(result);
                hideOpStatus();
            },
            error: function (error) {
                let errHtml = extractErrHtml(error);
                if (errHtml) {
                    displayResult(errHtml);
                    hideOpStatus();
                } else {
                    let errorMsg = extractErrMsg(error);
                    showOpError("Topic produce request failed:", errorMsg);
                }
            },
            complete: function() {
                produceBtn.prop("disabled", false).html('<span class="fas fa-paper-plane"></span> Produce Record');
            }
        });
    }

    function displayResult(result) {
        resultContainer.html(result).show();
        formatTimestampIn(resultContainer);
        $("html, body").animate({ scrollTop: resultContainer.offset().top }, 500);
    }

    function showError(message) {
        const errorHtml = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${message}
                <button type="button" class="close" data-dismiss="alert">
                    <span>&times;</span>
                </button>
            </div>
        `;
        resultContainer.html(errorHtml).show().animate({ scrollTop: resultContainer.offset().top }, 500);
    }
});
