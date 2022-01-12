<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <#include "../codeMirrorResources.ftl">
    <title>Kafkistry: Records Structure</title>
    <script src="static/recordsStructure-js/recordsStructure.js?ver=${lastCommit}"></script>
    <script src="static/recordsStructure-js/dryRunExamples.js?ver=${lastCommit}"></script>
    <script src="static/recordsStructure-js/dryRunInspect.js?ver=${lastCommit}"></script>
    <link rel="stylesheet" href="static/css/recordStructure.css?ver=${lastCommit}">
    <meta name="current-nav" content="nav-records-structure"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>

<div class="container">
    <h3>Records structure - dry-run inspect</h3>
    <hr/>

    <div class="form-row mb-2">
        <div class="col-4">
            <label>Input mode</label>
            <select name="inputMode" class="form-control" title="Input mode">
                <option value="SINGLE">Single record</option>
                <option value="MULTI">Multiple records (1 json per line)</option>
            </select>
        </div>
        <div class="col-4">
            <label>Encoding</label>
            <select name="encoding" class="form-control" title="Payload encoding type">
                <option>UTF8_STRING</option>
                <option>BASE64</option>
            </select>
        </div>
        <div class="col-4">
            <label>Examples</label>
            <select name="example" class="form-control" title="Selected example input">
                <option disabled selected>select...</option>
            </select>
        </div>
    </div>

    <div style="border: 1px solid #999999;">
        <textarea id="record-payload-editor" class="form-control width-full"
                  placeholder="enter record payload (json)"></textarea>
    </div>
    <br/>

    <div class="form-row">
        <div class="col-">
            <button id="format-json-btn" type="button" class="btn btn-secondary">Format JSON</button>
        </div>
        <div class="col">
            <button id="dry-run-inspect-records-structure-btn" type="button" class="btn btn-primary form-control">
                Dry-run inspect records structure
            </button>
        </div>
    </div>

    <#include "../common/serverOpStatus.ftl">
    <div id="dry-run-analyze-records-result" style="display: none;"></div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
