let chart = null;

function setupChart() {
    const ctx = document.getElementById('chart').getContext('2d');
    if (chart != null) {
        chart.destroy();
    }
    let rawData = getDataset();
    console.log(JSON.stringify(rawData, null, 4));
    let adaptedData = adaptDataset(rawData);
    console.log(JSON.stringify(adaptedData, null, 4));

    let charContainer = $("#chart-container");
    let chartComponentsToggleButton = $("button[data-target='#chart-components']");
    if (adaptedData.error) {
        if (!chartComponentsToggleButton.hasClass("collapsed")) {
            chartComponentsToggleButton.trigger('click');
        }
        charContainer.hide();
        setTimeout(function (){
            showOpErrorOnId("chart", "Can't display chart", adaptedData.error);
        }, 200);
        return;
    }

    hideServerOpOnId("chart");
    charContainer.show();
    if (chartComponentsToggleButton.hasClass("collapsed")) {
        chartComponentsToggleButton.trigger('click');
    }

    chart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: adaptedData.labels,
            datasets: adaptedData.datasets,
        },
        options: {
            responsive: false,
            scales: {
                xAxes: [
                    {
                        display: true,
                        type: 'category',
                        ticks: {
                            max: 3,
                            callback: function (value) {
                                let maxLength = 60;
                                if (value.length > maxLength) {
                                    return value.slice(0, maxLength) + "...";
                                }
                                return value;
                            }
                        },
                        scaleLabel: {
                            display: adaptedData.xAxisLabel !== undefined,
                            labelString: adaptedData.xAxisLabel
                        }
                    },
                ],
                yAxes: [
                    {
                        id: 'y1',
                        ticks: {
                            beginAtZero: true,
                            callback: function (value) {
                                return formatLabelValue(value, adaptedData.yAxisLabel);
                            }
                        },
                        scaleLabel: {
                            display: adaptedData.yAxisLabel !== undefined,
                            labelString: adaptedData.yAxisLabel
                        }
                    },
                    {
                        id: 'y2',
                        display: adaptedData.yAxis2Label !== undefined,
                        gridLines: {
                            display: false
                        },
                        ticks: {
                            beginAtZero: true,
                            callback: function (value) {
                                return formatLabelValue(value, adaptedData.yAxis2Label);
                            }
                        },
                        scaleLabel: {
                            display: adaptedData.yAxis2Label !== undefined,
                            labelString: adaptedData.yAxis2Label
                        },
                        position: 'right'
                    },
                    {
                        id: 'y3',
                        display: adaptedData.yAxis3Label !== undefined,
                        gridLines: {
                            display: false
                        },
                        ticks: {
                            beginAtZero: true,
                            callback: function (value) {
                                return formatLabelValue(value, adaptedData.yAxis3Label);
                            }
                        },
                        scaleLabel: {
                            display: adaptedData.yAxis3Label !== undefined,
                            labelString: adaptedData.yAxis3Label
                        },
                        position: 'right'
                    }
                ]
            },
            tooltips: {
                callbacks: {
                    beforeTitle: function () {
                        if (adaptedData.xAxisLabel) {
                            return adaptedData.xAxisLabel + ": ";
                        }
                        return "";
                    },
                    beforeLabel: function () {
                        if (adaptedData.xAxis2Label) {
                            return adaptedData.xAxis2Label + ": ";
                        }
                        return "";
                    },
                    label: function (tooltipItem, data) {
                        let label = data.datasets[tooltipItem.datasetIndex].label || '';
                        if (label) {
                            label += ': ';
                        }
                        let sourceDataset = adaptedData.datasets[tooltipItem.datasetIndex];
                        let yAxisLabel;
                        switch (sourceDataset.yAxisID) {
                            case undefined:
                            case "y1":
                                yAxisLabel = adaptedData.yAxisLabel;
                                break;
                            case "y2":
                                yAxisLabel = adaptedData.yAxis2Label;
                                break;
                            case "y3":
                                yAxisLabel = adaptedData.yAxis3Label;
                                break;
                        }
                        label += formatLabelValue(tooltipItem.yLabel, yAxisLabel, sourceDataset.label);
                        return label;
                    }
                }
            },
            animation: {
                duration: 0 // general animation time
            },
            hover: {
                animationDuration: 0 // duration of animations when hovering an item
            },
            responsiveAnimationDuration: 0 // animation duration after a resize
        }
    });
}

function formatLabelValue(value, yAxisLabel, datasetLabel) {
    if (yAxisLabel !== undefined && yAxisLabel !== "*") {
        let result = tryFormatValue(value, yAxisLabel);
        if (result) return result;
    }
    if (datasetLabel !== undefined) {
        let result = tryFormatValue(value, datasetLabel);
        if (result) return result;
    }
    return prettyNumber(value);
}

function tryFormatValue(value, label) {
    if (label.indexOf("BytesPerSec") >= 0) {
        return prettyBytesValue(value) + "/sec";
    }
    if (label.indexOf("Bytes") >= 0) {
        return prettyBytesValue(value);
    }
    if (label.length >= 2 && label.indexOf("Ms") === label.length - 2) {
        return prettyMillisValue(value);
    }
    if (label.indexOf("Rate") >= 0) {
        return prettyNumber(value) + "/sec";
    }
    if (label[label.length - 1] === '%') {
        return prettyNumber(value) + "%";
    }
    return undefined;
}

function getDataset() {
    let columns = [];
    $(".column-meta").each(function () {
        let column = $(this);
        columns.push({
            name: column.attr("data-label"),
            type: column.attr("data-type")
        });
    });

    let dataset = [];
    $(".data-row").each(function () {
        let row = [];
        $(this).find(".data-value").each(function () {
            let dataEntry = $(this);
            let rawValue = dataEntry.attr("data-value");
            let numericValue = Number(rawValue);
            let value;
            if (rawValue === "null") {
                value = null;
            } else if (isNaN(numericValue)) {
                value = rawValue;
            } else {
                value = numericValue;
            }
            row.push(value);
        });
        dataset.push(row);
    });
    return {
        columns: columns,
        rows: dataset
    };
}

function adaptDataset(dataset) {
    //column type cases
    // 0)  [] -> no data, no chart
    // 1)  [<label1>, ...<labelN>] -> only labels, no chart
    // 2)  [<number>] -> 1 dataset (use index as label)
    // 3)  [<label>, <number>] -> one dataset with labels
    // 4)  [<number1>, ...<numberM>]
    //  a) M = 2 numbers -> 1 dataset, use first number as label
    //  b) M > 2 numbers -> M datasets for each <numberX> (use index as label)
    // 5)  [<label1>, ...<labelN>, <number1>, ...<numberM>] -> M datasets for each <numberX> using joined labels as label
    // 6)  [<label1>, <label2>, ...<labelN>, <number>] ->
    //  a) if N <= 20 -> datasets grouped by label1
    //  b) if N > 20 -> one dataset with joined labels

    let columns = dataset.columns;
    let rows = dataset.rows;

    let numberOfNumeric = 0;
    for (let i = columns.length - 1; i >= 0; i--) {
        if (isNumericType(columns[i])) {
            numberOfNumeric++;
        } else {
            break;
        }
    }
    let numberOfLabels = columns.length - numberOfNumeric;

    let numNonNullValues = 0;
    rows.forEach(function (row) {
        row.slice(numberOfLabels, columns.length).forEach(function (value) {
            if (value != null) numNonNullValues++;
        });
    });

    // case 0)
    if (rows.length === 0 || columns.length === 0) {
        return {error: "No data"}
    }

    // case 0)
    if (numNonNullValues === 0) {
        return {error: "No non-null values to display"}
    }

    // case 1)
    if (numberOfNumeric === 0) {
        return {error: "Last column need to be numeric"}
    }

    // case 4a)
    if (numberOfLabels === 0 && numberOfNumeric === 2) {
        let labels = [];
        let dataset = {
            label: columns[1].name,
            data: []
        };
        rows.forEach(function (row) {
            labels.push(row[0].toString());
            dataset.data.push(row[1]);
        });
        return {
            labels: labels,
            datasets: [dataset],
            xAxisLabel: columns[0].name,
            yAxisLabel: columns[1].name
        }
    }

    // cases 4b), 5)
    if (numberOfNumeric > 1) {
        return adaptDatasetMultiValue(columns, numberOfLabels, rows);
    }

    // case 3)
    if (numberOfLabels === 1) {
        return adaptDatasetJoinLabelsOneDataset(columns, rows, numberOfLabels);
    }

    // cases 6a), 6b)
    return adaptDatasetGrouping(columns, rows, numberOfLabels);
}

let sqliteNumericTypes = [
    "INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "UNSIGNED BIG INT", "INT2", "INT8",
    "REAL", "DOUBLE", "DOUBLE PRECISION", "FLOAT",
    "NUMERIC"
]

function isNumericType(column) {
    if (column.name.indexOf("Id") === column.name.length - 2 ||
        column.name.indexOf("_") === 0 ||
        column.name.toLowerCase() === "partition" ||
        column.name.toLowerCase() === "rank") {
        return false;
    }
    let typeName = column.type;
    return sqliteNumericTypes.indexOf(typeName) >= 0 || typeName.indexOf("DECIMAL") >= 0;
}

function adaptDatasetJoinLabelsOneDataset(columns, rows, numberOfLabels) {
    let labels = [];
    let dataset = {
        label: columns[columns.length - 1].name,
        data: []
    };
    rows.forEach(function (row) {
        let label = row.slice(0, numberOfLabels).join(" - ");
        let value = row[row.length - 1];
        labels.push(label);
        dataset.data.push(value);
    });
    return {
        labels: labels,
        datasets: [dataset],
        xAxisLabel: columns.slice(0, numberOfLabels).map(function (column) {
            return column.name;
        }).join(" - "),
        yAxisLabel: columns[columns.length - 1].name
    };
}

function adaptDatasetMultiValue(columns, numberOfLabels, rows) {
    let labels = [];
    let datasets = [];
    let valueColumns = columns.slice(numberOfLabels, columns.length);
    let valueStats = valueColumns.map(function (column, index) {
        return analyzeValues(rows, index + numberOfLabels);
    });
    let yAxisColumns = partitionValueColumns(valueColumns, valueStats);
    yAxisColumns.columns.forEach(function (yColumn) {
        datasets.push({
            label: yColumn.column.name,
            yAxisID: yColumn.yAxis,
            data: []
        });
    });
    rows.forEach(function (row, index) {
        let key = row.slice(0, numberOfLabels).join(" - ");
        if (key === "") key = (index + 1).toString();
        labels.push(key);
        let values = row.slice(numberOfLabels, row.length);
        for (let i = 0; i < values.length; i++) {
            datasets[i].data.push(values[i]);
        }
    });
    return {
        labels: labels,
        datasets: datasets,
        xAxisLabel: columns.slice(0, numberOfLabels).map(function (column) {
            return column.name;
        }).join(" - "),
        yAxisLabel: yAxisColumns.y1 ? yAxisColumns.y1.label : undefined,
        yAxis2Label: yAxisColumns.y2 ? yAxisColumns.y2.label : undefined,
        yAxis3Label: yAxisColumns.y3 ? yAxisColumns.y3.label : undefined
    };
}

function analyzeValues(rows, columnIndex) {
    let min = undefined;
    let max = undefined;
    let onlyZeros = true;
    rows.forEach(function (row) {
        let value = row[columnIndex];
        if (value != null) {
            min = min !== undefined ? Math.min(min, value) : value;
            max = max !== undefined ? Math.max(max, value) : value;
        }
        if (value) {
            onlyZeros = false
        }
    });
    let absMax = (min !== undefined && max !== undefined) ? Math.max(max, -min) : undefined;
    return {onlyZeros: onlyZeros, min: min, max: max, absMax: absMax}
}

function partitionValueColumns(columns, valueStats) {
    let nonZeroStats = valueStats.filter((stats) => {
        return !stats.onlyZeros;
    });
    let pivotStats = nonZeroStats.sort((stats1, stats2) => {
        return stats1.absMax > stats2.absMax ? 1 : -1;
    })[Math.floor(nonZeroStats.length / 2)];

    let y1Labels = [];
    let y2Labels = [];
    let y3Labels = [];

    let partitionedColumns = [];
    columns.forEach(function (column, index) {
        partitionedColumns.push(null);
        let stats = valueStats[index];
        if (stats.onlyZeros) {
            return;
        }
        let similarMagnitude = isSimilarMagnitude(pivotStats.absMax, stats.absMax);
        let yAxis;
        if (stats.onlyZeros || similarMagnitude) {
            yAxis = "y1";
            y1Labels.push(column.name);
        } else if (stats.absMax > pivotStats.absMax) {
            yAxis = "y2";
            y2Labels.push(column.name);
        } else {
            yAxis = "y3";
            y3Labels.push(column.name);
        }
        partitionedColumns[index] = {
            column: column,
            yAxis: yAxis
        };
    })
    columns.forEach(function (column, index) {
        if (!valueStats[index].onlyZeros) {
            return;
        }
        let yAxis;
        if (y1Labels.length > 0 && isSimilarLabel(column.name, y1Labels[0])) {
            yAxis = "y1";
            y1Labels.push(column.name);
        } else if (y2Labels.length > 0 && isSimilarLabel(column.name, y2Labels[0])) {
            yAxis = "y2";
            y2Labels.push(column.name);
        } else if (y3Labels.length > 0 && isSimilarLabel(column.name, y3Labels[0])) {
            yAxis = "y3";
            y3Labels.push(column.name);
        } else {
            yAxis = "y1";
            y1Labels.push(column.name);
        }
        partitionedColumns[index] = {
            column: column,
            yAxis: yAxis
        };
    })
    return {
        columns: partitionedColumns,
        y1: {label: commonLabel(y1Labels)},
        y2: {label: commonLabel(y2Labels)},
        y3: {label: commonLabel(y3Labels)}
    };
}

function isSimilarMagnitude(v1, v2) {
    if (v1 === undefined || v2 === undefined) {
        return false;
    }
    let factor = 100;
    return (v1 < v2) ? v1 * factor > v2 : v2 * factor > v1;
}

function isSimilarLabel(l1, l2) {
    let l1Tokens = tokenizeLabel(l1);
    let l2Tokens = tokenizeLabel(l2);
    if (l1Tokens.length !== l2Tokens.length) {
        return false;
    }
    for (let i = 0; i < l1Tokens.length; i++) {
        if (l1Tokens[i] === l2Tokens[i]) {
            return true;
        }
    }
    return false;
}

function commonLabel(labels) {
    if (labels.length === 0) {
        return undefined;
    }
    if (labels.length === 1) {
        return labels[0];
    }
    let tokenizedLabels = labels.map(function (label) {
        return tokenizeLabel(label);
    });
    let commonTokens = [];
    let haveCommon = false;
    let length = tokenizedLabels[0].length
    for (let i = 0; i < length; i++) {
        let token = tokenizedLabels[0][i];
        for (let j = 0; j < tokenizedLabels.length; j++) {
            if (tokenizedLabels[j].length !== length) {
                let suffix = commonSuffix(tokenizedLabels);
                let prefix = commonPrefix(tokenizedLabels);
                if (!suffix && !prefix) {
                    return undefined;
                }
                if (prefix.length > suffix.length) {
                    return prefix + " *";
                } else {
                    return "* " + suffix;
                }
            }
            if (tokenizedLabels[j][i] !== token) {
                token = "*";
                break;
            }
        }
        commonTokens.push(token);
        if (token !== "*") {
            haveCommon = true;
        }
    }
    if (!haveCommon) {
        return undefined;
    }
    return commonTokens.join(" ");
}

function tokenizeLabel(label) {
    return label.split(/[\s_]|(?=[A-Z]|\W)|(?<=\W)/).filter(function (token) {
        return token.trim().length > 0;
    });
}

function commonPrefix(labelsTokens) {
    return common(labelsTokens, false);
}

function commonSuffix(labelsTokens) {
    return common(labelsTokens, true);
}

function common(labelsTokens, suffix) {
    let common = [];
    let minLength = labelsTokens.map(function (labelTokens) {
        return labelTokens.length;
    }).reduce(function (l1, l2) {
        return Math.min(l1, l2);
    });
    for (let i = 0; i < minLength; i++) {
        let token = labelsTokens[0][suffix ? labelsTokens[0].length - i - 1 : i];
        for (let j = 0; j < labelsTokens.length; j++) {
            if (token !== labelsTokens[j][suffix ? labelsTokens[j].length - i - 1 : i]) {
                return common;
            }
        }
        if (suffix) common.unshift(token);
        else common.push(token);
    }
    return common.join(" ");
}

function adaptDatasetGrouping(columns, rows, numOfLabels) {
    let rawGroups = {};
    for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
        let row = rows[rowIndex];
        let group = row[0];
        let rowLeftover = row.slice(1, row.length);
        if (rawGroups[group]) {
            rawGroups[group].push(rowLeftover);
        } else {
            rawGroups[group] = [rowLeftover];
        }
    }

    let groupKeyValues = {};
    Object.keys(rawGroups).forEach(function (group) {
        let groupRows = rawGroups[group];
        groupKeyValues[group] = rowsToKeyVal(groupRows);
    });

    let existingKeys = [];
    rows.forEach(function (row) {
        let key = row.slice(1, row.length - 1).join(" - ");
        if (existingKeys.indexOf(key) < 0) {
            existingKeys.push(key);
        }
    });

    // case 6b)
    if (existingKeys.length > 20) {
        return adaptDatasetJoinLabelsOneDataset(columns, rows, numOfLabels);
    }
    // else case 6a)

    existingKeys.forEach(function (key) {
        Object.keys(groupKeyValues).forEach(function (group) {
            if (!groupKeyValues[group][key]) {
                groupKeyValues[group][key] = null;
            }
        });
    });


    let labels = Object.keys(groupKeyValues);
    let datasets = [];

    existingKeys.forEach(function (key) {
        let data = [];
        labels.forEach(function (group) {
            data.push(groupKeyValues[group][key]);
        });
        datasets.push({
            label: key,
            data: data
        });
    });

    return {
        labels: labels,
        datasets: datasets,
        xAxisLabel: columns[0].name,
        xAxis2Label: columns.slice(1, columns.length - 1).map(function (column) {
            return column.name;
        }).join(" - "),
        yAxisLabel: columns[columns.length - 1].name
    };
}

function rowsToKeyVal(rows) {
    let keyValues = {};
    for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
        let row = rows[rowIndex];
        let key = row.slice(0, row.length - 1).join(" - ");
        keyValues[key] = row[row.length - 1];
    }
    return keyValues;
}


