<div class="card">
    <div class="card-header">
        <h4>Representation as yaml</h4>
        <span>File name: <span style="font-family: monospace;" id="filename"></span></span>
        <div style="display: none;" id="diff-against-input" class="pt-2">
            Show diff against current content in:
            <label class="btn btn-sm btn-outline-primary active">
                Branch <input type="radio" name="diff-source" value="branch" checked>
            </label>
            <label class="btn btn-sm btn-outline-primary">
                Master <input type="radio" name="diff-source" value="master">
            </label>
        </div>
    </div>

    <div class="card-body p-0">
        <pre class="border m-0" id="config-yaml"></pre>
    </div>
</div>
