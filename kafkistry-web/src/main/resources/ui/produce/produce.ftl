<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="clusterTopics" type="java.util.Map<java.lang.String, java.util.List<java.lang.String>>" -->
<#-- @ftlvariable name="allClusters" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="availableSerializerTypes" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="defaultKeySerializer" type="java.lang.String" -->
<#-- @ftlvariable name="defaultValueSerializer" type="java.lang.String" -->
<#-- @ftlvariable name="defaultHeaderSerializer" type="java.lang.String" -->
<#-- @ftlvariable name="sampleKey" type="java.lang.String" -->
<#-- @ftlvariable name="sampleValue" type="java.lang.String" -->
<#-- @ftlvariable name="sampleHeaders" type="java.util.List" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "../codeMirrorResources.ftl"/>
    <script src="static/produce/produce.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Produce</title>
    <meta name="current-nav" content="nav-produce"/>
</head>

<style>
    .topic-input {
        width: 500px !important;
    }
    .header-row {
        margin-bottom: 10px;
    }
    .CodeMirror, .CodeMirror-scroll {
        height: auto !important;
        min-height: 50px;
        max-height: 75vh;
    }
</style>

<body>

<#include "../commonMenu.ftl">

<div class="container">

    <h2 class="mt-2"> Produce Record to Kafka</h2>

    <div class="alert alert-warning" role="alert">
        <strong>Warning:</strong> This will produce a real record to the selected Kafka topic.
        Make sure you understand the impact before proceeding.
    </div>

    <#if !clusterIdentifier?? && allClusters?size == 1>
        <#assign clusterIdentifier = allClusters?first>
    </#if>

    <div id="produce-form">
        <div class="card">
            <div class="card-header">
                <h5>Target Selection</h5>
            </div>
            <div class="card-body">
                <div class="row">
                    <div class="col">
                        <label for="cluster-select">Cluster:</label>
                        <select id="cluster-select" name="cluster" class="form-control selectPicker"
                                data-live-search="true" data-size="6" required>
                            <option value="" disabled selected>Select cluster...</option>
                            <#list allClusters as cluster>
                                <option value="${cluster}"
                                        <#if clusterIdentifier?? && clusterIdentifier==cluster>selected</#if>>
                                    ${cluster}
                                </option>
                            </#list>
                        </select>
                    </div>
                    <div class="col">
                        <label for="topic-input">Topic:</label>
                        <input type="search" id="topic-input" placeholder="Select topic..." name="topic"
                               class="topic-input form-control" value="${topicName!""}" required>
                    </div>
                </div>
            </div>
        </div>

        <br/>

        <div class="card">
            <div class="card-header">
                <h5>Record Content</h5>
            </div>
            <div class="card-body">
                <div class="row">
                    <div class="col-auto">
                        <label class="btn btn-outline-secondary">
                            <input type="checkbox" id="null-key-checkbox"> Null Key
                        </label>
                    </div>
                    <div class="col" id="key-section">
                        <div class="row">
                            <div class="col-auto">
                                <div class="input-group">
                                    <label for="key-serializer" class="input-group-text input-group-prepend">Key Serializer:</label>
                                    <select id="key-serializer" class="form-control">
                                        <#list availableSerializerTypes as type>
                                            <option value="${type}"
                                                    <#if type == defaultKeySerializer>selected</#if>>${type}</option>
                                        </#list>
                                    </select>
                                </div>
                            </div>
                            <div class="col">
                                <input type="text" id="key-value" class="form-control"
                                       placeholder="Enter key value..." title="Record key">
                            </div>
                        </div>
                    </div>
                </div>

                <hr/>

                <div class="form-group">
                    <label class="btn btn-outline-secondary">
                        <input type="checkbox" id="null-value-checkbox"> Null Value (Tombstone)
                    </label>
                    <div id="value-section">
                        <div class="input-group">
                            <label for="value-serializer" class="input-group-text input-group-prepend">Value Serializer:</label>
                            <select id="value-serializer" class="form-control">
                                <#list availableSerializerTypes as type>
                                    <option value="${type}"
                                            <#if type == defaultValueSerializer>selected</#if>>${type}</option>
                                </#list>
                            </select>
                        </div>
                        <div style="border: 1px solid #999999; margin-top: 10px;">
                            <textarea id="value-value" class="form-control value-textarea"
                                      placeholder='Enter value...' title="Record value payload"></textarea>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <br/>

        <div class="card">
            <div class="card-header">
                <h5>Headers (Optional)</h5>
            </div>
            <div class="card-body">
                <div id="headers-container">
                </div>
                <button type="button" id="add-header-btn" class="btn btn-sm btn-secondary">
                    <span class="fas fa-plus"></span> Add Header
                </button>
            </div>
        </div>

        <br/>

        <div class="card">
            <div class="card-header">
                <button type="button" class="btn btn-sm btn-link" data-toggle="collapse"
                        data-target="#advanced-options">
                    <span class="fas fa-cog"></span> Advanced Options
                </button>
            </div>
            <div id="advanced-options" class="collapse">
                <div class="card-body">
                    <div class="form-group">
                        <label for="partition-input">Partition (optional):<span id="partition-info" class="text-muted small"></span></label>
                        <input type="number" id="partition-input" class="form-control"
                               placeholder="Leave empty for automatic partitioning">
                    </div>

                    <div class="form-group">
                        <label for="timestamp-input">Timestamp (optional):</label>
                        <input type="number" id="timestamp-input" class="form-control"
                               placeholder="Leave empty for broker-assigned timestamp">
                    </div>
                </div>
            </div>
        </div>

        <br/>

        <div class="form-group">
            <button type="submit" id="produce-btn" class="btn btn-primary btn-lg">
                <span class="fas fa-paper-plane"></span> Produce Record
            </button>
        </div>
    </div>

    <#include "../common/serverOpStatus.ftl">
    <div id="result-container" style="display: none;">
    </div>
</div>

<script>
    const clusterTopics = {};
    <#list clusterTopics as cluster, topics>
        clusterTopics["${cluster}"] = [
            <#list topics as topic>"${topic}"<#sep>,</#sep></#list>
        ];
    </#list>
    const defaultHeaderSerializer = "${defaultHeaderSerializer}";
    const initialSampleData = {
        key: ${(sampleKey???then('"' + sampleKey?js_string + '"', 'null'))},
        value: ${(sampleValue???then('"' + sampleValue?js_string + '"', 'null'))},
        headers: [
            <#if sampleHeaders??>
                <#list sampleHeaders as header>
                    {name: "${header.name?js_string}", value: "${header.value?js_string}"}<#sep>,</#sep>
                </#list>
            </#if>
        ]
    };
</script>

</body>
</html>
