<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="labels" type="java.util.List<com.infobip.kafkistry.model.Label>" -->

<#macro labelEntry category name externalId>
    <div class="label-entry form-group row g-2 mb-0 align-items-center">
        <label class="col-auto pr-0">
            <button class="move-label-up-btn btn btn-sm btn-outline-info" title="Move label UP">&uarr;</button>
        </label>
        <label class="col-auto">
            <button class="move-label-down-btn btn btn-sm btn-outline-info" title="Move label DOWN">&darr;</button>
        </label>
        <label class="col-2">
            <input type="text" name="label-category" class="form-control" value="${category}" placeholder="category..." title="Label category">
        </label>
        <label class="col-4">
            <input type="text" name="label-name" class="form-control" value="${name}" placeholder="name..." title="Label name">
        </label>
        <label class="col-3">
            <input type="text" name="label-external-id" class="form-control" value="${externalId}" placeholder="external ID (optional)"  title="Label external ID">
        </label>
        <label class="col-auto">
            <button class="remove-label-btn btn btn-sm btn-outline-danger mt-1">x</button>
        </label>
    </div>
</#macro>


<label class="col-sm-2 col-form-label" for="labels-list">Labels</label>
<div class="col-10">
    <div class="labels">
        <#list labels as label>
            <@labelEntry category=label.category name=label.name externalId=label.externalId!''/>
        </#list>
    </div>
    <select class="choose-label-select" title="Add label..."
            data-live-search="true" data-size="8" data-style="bg-primary-subtle btn-sm">
        <option value="new"
                data-category=""
                data-name=""
                data-external-id="" data-content="<span class='badge bg-primary'>CREATE NEW...</span>"></option>
        <#list existingValues.topicLabels as label>
            <option value="${label.category}-${label.name}-${label.externalId!''}"
                    data-category="${label.category}"
                    data-name="${label.name}"
                    data-external-id="${label.externalId!''}"
                    data-content="<span class='badge bg-secondary'>${label.category}</span> ${label.name}"
            ></option>
        </#list>
    </select>
</div>

<div style="display: none;">
    <div id="label-entry-template">
        <@labelEntry category='' name='' externalId=''/>
    </div>
    <#list existingValues.topicLabels as existingLabel>
        <div class="existing-label"
             data-category="${existingLabel.category}"
             data-name="${existingLabel.name}"
             data-extarnal-id="${existingLabel.externalId!''}"
        ></div>
    </#list>
</div>
