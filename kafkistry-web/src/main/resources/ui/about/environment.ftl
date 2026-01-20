<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="envProperties" type="com.infobip.kafkistry.api.EnvironmentPropertiesDto" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: About - Environment</title>
    <meta name="current-nav" content="nav-app-info"/>
    <style>
        .sensitive-value {
            font-family: monospace;
            background-color: #343a40;
            color: #fff;
            padding: 2px 4px;
            border-radius: 3px;
        }
        .property-key {
            font-family: monospace;
            font-size: 0.9em;
            word-break: break-word;
            overflow-wrap: break-word;
            white-space: normal;
            max-width: 300px;
        }
        .property-value {
            font-family: monospace;
            font-size: 0.9em;
            word-break: break-all;
        }
        .tab-pane {
            padding-top: 1rem;
        }
        .value-container {
            position: relative;
        }
        .value-text.truncated {
            display: inline;
        }
        .value-text.truncated .full-text {
            display: none;
        }
        .value-text.expanded .truncated-text {
            display: none;
        }
        .expand-toggle {
            cursor: pointer;
            color: #007bff;
            text-decoration: underline;
            font-size: 0.85em;
            margin-left: 4px;
            white-space: nowrap;
        }
        .expand-toggle:hover {
            color: #0056b3;
        }
    </style>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <#assign activeNavItem = "environment">
    <#include "submenu.ftl">

    <#-- Warning banner -->
    <div class="alert alert-warning">
        <strong>Sensitive Data Warning:</strong> This page displays environment variables and Spring property sources.
        Sensitive values (passwords, secrets, tokens, keys) are masked by default for security.
    </div>

    <#-- Active Profiles -->
    <div class="card mb-3">
        <div class="card-body">
            <h5 class="card-title mb-2">Active Spring Profiles</h5>
            <#if envProperties.activeProfiles?size == 0>
                <span class="badge bg-secondary">default</span>
                <span class="text-muted ml-2"><i>(no profiles active)</i></span>
            <#else>
                <#list envProperties.activeProfiles as profile>
                    <span class="badge bg-primary mr-1">${profile}</span>
                </#list>
            </#if>
        </div>
    </div>

    <#-- Summary stats and toggle button -->
    <div class="card mb-3">
        <div class="card-body">
            <div class="row">
                <div class="col-md-3">
                    <strong>Total Sources:</strong> ${envProperties.propertySources?size}
                </div>
                <div class="col-md-3">
                    <strong>Total Properties:</strong> ${envProperties.allProperties?size}
                </div>
                <div class="col-md-3">
                    <#assign sensitiveCount = 0>
                    <#list envProperties.allProperties as prop>
                        <#if prop.sensitive>
                            <#assign sensitiveCount = sensitiveCount + 1>
                        </#if>
                    </#list>
                    <strong>Sensitive Properties:</strong> ${sensitiveCount}
                </div>
                <div class="col-md-3 text-end">
                    <button id="toggle-mask-btn" class="btn btn-sm btn-outline-warning">
                        Show Sensitive Values
                    </button>
                </div>
            </div>
        </div>
    </div>

    <#-- Tab navigation -->
    <ul class="nav nav-tabs" id="envTabs" role="tablist">
        <li class="nav-item">
            <a class="nav-link active" id="by-source-tab" data-bs-toggle="tab" href="#by-source" role="tab">
                By Property Source
            </a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="all-props-tab" data-bs-toggle="tab" href="#all-props" role="tab">
                All Properties
            </a>
        </li>
    </ul>

    <div class="tab-content" id="envTabContent">

        <#-- Tab 1: By Property Source -->
        <div class="tab-pane fade show active" id="by-source" role="tabpanel">
            <#list envProperties.propertySources as source>
                <div class="card mb-2">
                    <div class="card-header collapsed" data-toggle="collapsing"
                         data-bs-target="#props-${source?index?c}">
                        <h5>
                            <span class="when-collapsed" title="expand...">▼</span>
                            <span class="when-not-collapsed" title="collapse...">△</span>
                            <span class="badge bg-secondary">${source.type}</span>
                            ${source.name}
                            <span class="badge bg-neutral">${source.properties?size} properties</span>
                        </h5>
                    </div>
                    <div id="props-${source?index?c}" class="card-body p-0 collapseable">
                        <table class="table table-hover table-sm table-bordered m-0">
                            <thead class="table-theme-accent">
                                <tr>
                                    <th style="width: 30%;">Property Name</th>
                                    <th style="width: 30%;">Raw Value</th>
                                    <th style="width: 30%;">Resolved Value</th>
                                    <th style="width: 10%;">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <#list source.properties as prop>
                                    <tr>
                                        <td class="property-key">${prop.key}</td>
                                        <td class="property-value">
                                            <#if prop.sensitive>
                                                <span class="sensitive-value masked-value"
                                                      data-value="${prop.value!''}">***MASKED***</span>
                                            <#else>
                                                <code>${prop.value!''}</code>
                                            </#if>
                                        </td>
                                        <td class="property-value">
                                            <#if prop.error??>
                                                <span class="badge bg-danger">Resolution error</span>
                                                <code>${prop.error}</code>
                                            <#elseif prop.sensitive>
                                                <span class="sensitive-value masked-value"
                                                      data-value="${prop.resolvedValue!''}">***MASKED***</span>
                                            <#else>
                                                <#if prop.resolvedValue?? && prop.value != prop.resolvedValue>
                                                    <code class="text-primary">${prop.resolvedValue}</code>
                                                <#elseif prop.resolvedValue??>
                                                    <code class="text-muted"><i>(same)</i></code>
                                                <#else>
                                                    <code class="text-muted"><i>(unresolved)</i></code>
                                                </#if>
                                            </#if>
                                        </td>
                                        <td>
                                            <#if prop.sensitive>
                                                <span class="badge bg-warning">Sensitive</span>
                                            <#else>
                                                <span class="badge bg-success">Visible</span>
                                            </#if>
                                        </td>
                                    </tr>
                                </#list>
                            </tbody>
                        </table>
                    </div>
                </div>
            </#list>

            <#if envProperties.propertySources?size == 0>
                <p class="mt-3"><i>(no property sources found)</i></p>
            </#if>
        </div>

        <#-- Tab 2: All Properties -->
        <div class="tab-pane fade" id="all-props" role="tabpanel">
            <div class="card">
                <div class="card-body px-0 py-2">
                    <table class="table table-hover table-sm table-bordered m-0 datatable" id="all-props-table">
                        <thead class="table-theme-dark">
                            <tr>
                                <th style="width: 25%;">Property Name</th>
                                <th style="width: 24%;">Raw Value</th>
                                <th style="width: 23%;">Resolved Value</th>
                                <th style="width: 21%;">Source</th>
                                <th style="width: 7%;">Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            <#list envProperties.allProperties as prop>
                                <tr>
                                    <td class="property-key">${prop.key}</td>
                                    <td class="property-value">
                                        <#if prop.sensitive>
                                            <span class="sensitive-value masked-value"
                                                  data-value="${prop.value!''}">***MASKED***</span>
                                        <#else>
                                            <code>${prop.value!''}</code>
                                        </#if>
                                    </td>
                                    <td class="property-value">
                                        <#if prop.error??>
                                                <span class="badge bg-danger">Resolution error</span>
                                                <code>${prop.error}</code>
                                        <#elseif prop.sensitive>
                                            <span class="sensitive-value masked-value"
                                                  data-value="${prop.resolvedValue!''}">***MASKED***</span>
                                        <#else>
                                            <#if prop.resolvedValue?? && prop.value != prop.resolvedValue>
                                                <code class="text-primary">${prop.resolvedValue}</code>
                                            <#elseif prop.resolvedValue??>
                                                <code class="text-muted"><i>(same)</i></code>
                                            <#else>
                                                <code class="text-muted"><i>(unresolved)</i></code>
                                            </#if>
                                        </#if>
                                    </td>
                                    <td><small>${prop.origin!''}</small></td>
                                    <td>
                                        <#if prop.sensitive>
                                            <span class="badge bg-warning">Sensitive</span>
                                        <#else>
                                            <span class="badge bg-success">Visible</span>
                                        </#if>
                                    </td>
                                </tr>
                            </#list>
                        </tbody>
                    </table>
                </div>
            </div>

            <#if envProperties.allProperties?size == 0>
                <p class="mt-3"><i>(no properties found)</i></p>
            </#if>
        </div>

    </div>

</div>

<script>
    $(document).ready(function() {
        let isMasked = true;
        let allPropsTable = $('#all-props-table');
        let toggleMaskBtn = $('#toggle-mask-btn');
        const MAX_LENGTH = 150; // Character limit before truncation

        // Wait for the global datatable.js to initialize the table, then get a reference to it
        function getDataTable() {
            if (allPropsTable.length > 0 && $.fn.DataTable.isDataTable('#all-props-table')) {
                return allPropsTable.DataTable();
            }
            return null;
        }

        // Function to process a single element for truncation
        function processElementForTruncation($element) {
            // Skip if already processed
            if ($element.closest('.value-container').length > 0) {
                return;
            }

            const text = $element.text();
            const isLong = text.length > MAX_LENGTH;

            if (isLong && text !== '***MASKED***' && !text.match(/^\(.*\)$/)) {
                const truncatedText = text.substring(0, MAX_LENGTH) + '...';
                const elementClasses = $element.attr('class') || '';
                const isSensitive = $element.hasClass('sensitive-value');
                const dataValue = $element.attr('data-value');

                // Create container with truncated and full text
                const $container = $('<div class="value-container"></div>');
                const $valueText = $('<span class="value-text truncated"></span>');

                if (isSensitive) {
                    $valueText.html(
                        '<span class="sensitive-value masked-value truncated-text" data-value="' +
                        (dataValue || '') + '">' + truncatedText + '</span>' +
                        '<span class="sensitive-value masked-value full-text" data-value="' +
                        (dataValue || '') + '">' + text + '</span>'
                    );
                } else {
                    $valueText.html(
                        '<code class="' + elementClasses + ' truncated-text">' + truncatedText + '</code>' +
                        '<code class="' + elementClasses + ' full-text">' + text + '</code>'
                    );
                }

                const $toggle = $('<span class="expand-toggle">Show more</span>');

                $container.append($valueText);
                $container.append($toggle);

                $element.replaceWith($container);

                // Add click handler for toggle
                $toggle.on('click', function() {
                    const $valueTextElem = $(this).siblings('.value-text');
                    if ($valueTextElem.hasClass('truncated')) {
                        $valueTextElem.removeClass('truncated').addClass('expanded');
                        $(this).text('Show less');
                    } else {
                        $valueTextElem.removeClass('expanded').addClass('truncated');
                        $(this).text('Show more');
                    }
                });
            }
        }

        // Function to truncate long values in DataTable
        function truncateDataTableValues() {
            let allPropsDt = getDataTable();
            if (allPropsDt) {
                try {
                    allPropsDt.cells().every(function() {
                        let $cell = $(this.node());
                        // Only process property-value cells
                        if ($cell.hasClass('property-value')) {
                            $cell.find('code, .sensitive-value').each(function() {
                                processElementForTruncation($(this));
                            });
                        }
                    });
                } catch (e) {
                    console.error('Error truncating DataTable values:', e);
                }
            }
        }

        // Function to truncate long values in non-DataTable elements
        function truncateNonDataTableValues() {
            // Process only the "By Property Source" tab
            $('#by-source .property-value code, #by-source .property-value .sensitive-value').each(function() {
                processElementForTruncation($(this));
            });
        }

        // Initial truncation after a short delay to ensure DOM is ready
        setTimeout(function() {
            truncateNonDataTableValues();
            truncateDataTableValues();
        }, 100);

        // Re-apply truncation when tabs are switched
        $('a[data-bs-toggle="tab"]').on('shown.bs.tab', function(e) {
            // Force DataTable to adjust column widths when All Properties tab is shown
            if ($(e.target).attr('href') === '#all-props') {
                let dt = getDataTable();
                if (dt) {
                    dt.columns.adjust().draw(false);
                }
            }

            setTimeout(function() {
                truncateNonDataTableValues();
                truncateDataTableValues();
            }, 100);
        });

        // Re-apply truncation when property source sections are expanded
        $('[data-toggle="collapsing"]').on('click', function() {
            setTimeout(function() {
                truncateNonDataTableValues();
            }, 300);
        });

        // Hook into DataTable draw events for pagination/filtering/sorting
        let dtCheckInterval = setInterval(function() {
            let dt = getDataTable();
            if (dt) {
                clearInterval(dtCheckInterval);

                // Apply truncation on DataTable draw events
                dt.on('draw', function() {
                    setTimeout(function() {
                        truncateDataTableValues();
                    }, 50);
                });
            }
        }, 100);

        // Toggle mask/unmask functionality
        toggleMaskBtn.click(function() {
            isMasked = !isMasked;

            if (isMasked) {
                toggleMaskBtn.text('Show Sensitive Values');
            } else {
                toggleMaskBtn.text('Hide Sensitive Values');
            }

            // Update all masked values using DataTables API for the All Properties table
            let allPropsDt = getDataTable();
            if (allPropsDt) {
                try {
                    allPropsDt.cells().every(function() {
                        let $cell = $(this.node());
                        let $maskedValue = $cell.find('.masked-value');

                        if ($maskedValue.length > 0) {
                            const actualValue = $maskedValue.attr('data-value');
                            if (isMasked) {
                                $maskedValue.text('***MASKED***');
                            } else {
                                $maskedValue.text(actualValue);
                            }
                        }
                    });
                } catch (e) {
                    console.error('Error updating DataTable values:', e);
                }
            }

            // Update all masked values using simple jQuery (fallback for all tables)
            $('.masked-value').each(function() {
                const $this = $(this);
                const actualValue = $this.attr('data-value');
                if (isMasked) {
                    $this.text('***MASKED***');
                } else {
                    $this.text(actualValue);
                }
            });
        });
    });
</script>

<#include "../common/pageBottom.ftl">
</body>
</html>
