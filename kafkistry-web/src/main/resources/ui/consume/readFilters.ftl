<div class="p-2">
    <div id="filter-rules"></div>

    <div id="filters-mode" class="mt-2" style="display: none;">
        <div class="clearfix"></div>
        <label class="form-inline" id="filters-mode">
            <span class="mr-1">Filtering mode: </span>
            <select name="filtersMode" class="form-control form-control-sm">
                <option value="ALL">All filters need to match</option>
                <option value="ANY">Any filter can match</option>
                <option value="NONE">None of filters is allowed to match</option>
            </select>
        </label>
    </div>

    <button id="addRuleBtn" type="button" class="mt-2 btn btn-sm btn-outline-primary">Add filter rule...</button>
</div>

<div id="filter-rule-template" style="display: none;">
    <div class="filter-rule form-row">
        <label class="m-0 p-0 col-">
            <select name="targetType" class="form-control form-control-sm">
                <option value="JSON_FIELD">Json field</option>
                <option value="RECORD_HEADER">Header</option>
                <option value="RECORD_KEY">Key</option>
            </select>
        </label>
        <label class="m-0 p-0 col text-monospace">
            <input name="keyName" type="text" placeholder="key name" class="form-control form-control-sm">
        </label>
        <label class="m-0 p-0 col-">
            <select name="ruleType" class="form-control form-control-sm">
                <option value="EXIST">Exists</option>
                <option value="NOT_EXIST">Does not exist</option>
                <option value="IS_NULL">Is null</option>
                <option value="NOT_NULL">Is not null</option>
                <option value="EQUAL_TO" selected>Equals to</option>
                <option value="NOT_EQUAL_TO">Does not equal to</option>
                <option value="LESS_THAN">Is less than</option>
                <option value="GREATER_THAN">Is greater than</option>
                <option value="CONTAINS">Contains</option>
                <option value="NOT_CONTAINS">Does not contain</option>
                <option value="REGEX">Contains RegEx</option>
                <option value="NOT_REGEX">Not contains RegEx</option>
            </select>
        </label>
        <label class="m-0 p-0 col">
            <input name="value" type="text" placeholder="value" class="form-control form-control-sm">
        </label>
        <label class="m-0 p-0 col-">
            <button type="button" class="remove-rule-btn btn btn-sm btn-outline-danger">x</button>
        </label>
    </div>
</div>
