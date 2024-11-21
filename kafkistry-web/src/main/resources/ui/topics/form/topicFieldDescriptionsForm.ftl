<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="fieldDescriptions" type="java.util.List<com.infobip.kafkistry.model.FieldDescription>" -->

<#macro fieldClassification classification>
    <div class="field-classification input-group input-group-sm">
        <input class="form-control" name="field-classification" placeholder="Classification..."
               title="Field classification" value="${classification}">
        <div class="input-group-append">
            <div class="remove-field-classification-btn btn btn-outline-danger">x</div>
        </div>
    </div>
</#macro>

<#macro fieldDescriptionEntry fieldSelector classifications description>
<#-- @ftlvariable name="classifications" type="java.util.List<java.lang.String>" -->
    <div class="topic-field-description">
        <div class="row m-n2">
            <div class="col-4 px-2">
                <input class="form-control" name="field-selector" placeholder="Field..." title="Field name/selector"
                       value="${fieldSelector}">
            </div>
            <div class="col-2 px-2">
                <div class="field-classifications">
                    <#list classifications as classification>
                        <@fieldClassification classification=classification/>
                    </#list>
                </div>
                <div class="add-field-classification-btn btn btn-sm btn-outline-primary w-100">
                    Add classification...
                </div>
            </div>
            <div class="col px-2">
                <textarea class="form-control" name="field-description" placeholder="Enter description..."
                          title="Field description">${description}</textarea>
            </div>
            <div class="col-auto px-2">
                <div class="remove-field-description-btn btn btn-outline-danger">x</div>
            </div>
        </div>
        <hr/>
    </div>
</#macro>


<div class="field-descriptions">
    <#list fieldDescriptions as fieldDescription>
        <@fieldDescriptionEntry fieldSelector=fieldDescription.selector
        classifications=fieldDescription.classifications description=fieldDescription.description/>
    </#list>
</div>
<div class="add-topic-field-description-btn btn btn-outline-primary">
    Add field description...
</div>
<div id="topic-field-description-template" class="template" style="display: none;">
    <@fieldDescriptionEntry fieldSelector="" classifications=[] description=""/>
</div>
<div id="topic-field-classification-template" class="template" style="display: none;">
    <@fieldClassification classification=""/>
</div>