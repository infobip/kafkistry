<#-- @ftlvariable name="json" type="com.fasterxml.jackson.databind.ObjectMapper" -->

<#assign collapseIdGen = 1>
<#function nextCollapseId>
    <#assign collapseId = collapseIdGen>
    <#assign collapseIdGen = collapseIdGen + 1>
    <#return "collapse-${collapseId}">
</#function>

<#macro showFieldValue fieldValue>
<#-- @ftlvariable name="fieldValue" type="com.infobip.kafkistry.model.RecordFieldValue" -->
    <small>
        <strong>Values:</strong>
        <#if fieldValue.highCardinality>
            <span class="badge badge-danger">HIGH CARDINALITY</span>
        <#elseif fieldValue.tooBig>
            <span class="badge badge-danger">TOO BIG</span>
        <#elseif fieldValue.valueSet??>
            [
            <#list fieldValue.valueSet as val>
                <#assign valAsString>
                    <#attempt>
                        <#if val?is_boolean>${val?then('true', 'false')}
                        <#elseif val?is_number>${val?c}
                        <#elseif val?is_string>${val}
                        <#else>${val?api.toString()}</#if>
                        <#recover>
                            ???
                    </#attempt>
                </#assign>
                <span class="text-secondary">${valAsString}</span><#if !val?is_last>,</#if>
            </#list>
            ]
        </#if>
    </small>
</#macro>

<#macro showFieldInfo field parentType="" fullName=false>
<#-- @ftlvariable name="field" type="com.infobip.kafkistry.model.RecordField" -->
    <span class="field-type-tag field-type-${field.type} badge badge-secondary" title="Field type">
        ${field.type}
    </span>
    <#if fullName>
        <#if (field.fullName)??>
            <code class="ml-2" title="Full field name">${field.fullName}</code>
        <#else>
            <span class="badge badge-info" title="Full name = null">ROOT</span>
        </#if>
        <#if field.nullable>
            <span class="badge badge-dark">NULLABLE</span>
        </#if>
    <#else>
        <#if field.nullable>
            <span class="badge badge-dark">NULLABLE</span>
        </#if>
        <#if (field.name)??>
            <code class="ml-2" title="Field name">${field.name}</code>
        <#else>
            <#assign name>
                <#switch parentType>
                    <#case "OBJECT">*<#break>
                    <#case "ARRAY">[*]<#break>
                    <#default>
                </#switch>
            </#assign>
            <#if name?has_content>
                <code class="ml-2" title="Field name">${name}</code> <i class="small text-primary">(variable)</i>
            </#if>
        </#if>
    </#if>
    <#if field.value??>
        <@showFieldValue fieldValue=field.value/>
    </#if>
</#macro>

<#macro showRecordField field parentType>
<#-- @ftlvariable name="field" type="com.infobip.kafkistry.model.RecordField" -->
<#-- @ftlvariable name="parentType" type="com.infobip.kafkistry.model.RecordFieldType" -->
    <#assign hasChildren = (field.children)?? && field.children?size gt 0>
    <div class="ml-5 m-1 <#if hasChildren>border-left</#if>">
        <@showFieldInfo field=field parentType=parentType/>
        <#if hasChildren>
            <div class="m-2 mt-1">
                <#assign collapseTarget = nextCollapseId()>
                <button class="btn btn-sm btn-primary" type="button" data-toggle="collapse"
                        data-target="#${collapseTarget}">
                    Sub-fields (${field.children?size})
                    <span class="if-collapsed" title="expand...">▼</span>
                    <span class="if-not-collapsed" title="collapse...">△</span>
                </button>
                <div id="${collapseTarget}" class="collapse show">
                    <#list field.children as childField>
                        <@showRecordField field=childField parentType=field.type/>
                    </#list>
                </div>
            </div>
        </#if>
    </div>
</#macro>

<#function fieldsFlat jsonFields>
<#-- @ftlvariable name="jsonFields" type="java.util.List<com.infobip.kafkistry.model.RecordField>" -->
    <#assign fields = []>
    <#list jsonFields as f>
        <#assign fields += [f] + ((f.children)??)?then(fieldsFlat(f.children), [])>
    </#list>
    <#return fields>
</#function>

<#macro showPayloadStructure structure>
<#-- @ftlvariable name="structure" type="com.infobip.kafkistry.model.RecordsStructure" -->
    <div>
        Record root structure type: <span class="badge badge-secondary">${structure.payloadType}</span>
        <#if structure.nullable>
            <span class="badge badge-dark">NULLABLE</span>
        </#if>
        <#if (structure.jsonFields)??>
            <div>
                <label class="btn btn-sm btn-outline-secondary">
                    Tree view
                    <input type="radio" name="fields-display-view-type" value="tree" checked>
                </label>
                <label class="btn btn-sm btn-outline-secondary">
                    List view
                    <input type="radio" name="fields-display-view-type" value="list">
                </label>
                <label class="btn btn-sm btn-outline-secondary">
                    Raw view
                    <input type="radio" name="fields-display-view-type" value="raw">
                </label>
            </div>
            <div class="fields-tree" style="display: none;">
                <#list structure.jsonFields as jsonField>
                    <@showRecordField field=jsonField parentType=""/>
                </#list>
            </div>
            <div class="fields-list" style="display: none;">
                <#assign fields = fieldsFlat(structure.jsonFields)>
                <#list fields as field>
                    <@showFieldInfo field=field fullName=true/><br/>
                </#list>
            </div>
            <div class="fields-raw" style="display: none;">
                <#assign structureJson = json.writerWithDefaultPrettyPrinter().writeValueAsString(structure.jsonFields)>
                <code><pre>${structureJson}</pre></code>
            </div>
        </#if>
    </div>
</#macro>

<#macro showStructure structure>
<#-- @ftlvariable name="structure" type="com.infobip.kafkistry.model.RecordsStructure" -->
    <div class="card">
        <div class="card-header h5">Headers</div>
        <div class="card-body p-1">
            <#if structure.headerFields?? && structure.headerFields?size gt 0 && structure.headerFields?first.children?size gt 0>
                <#list structure.headerFields?first.children as headerField>
                    <@showRecordField field=headerField parentType=""/>
                </#list>
            <#else>
                <i>(no headers)</i>
            </#if>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header h5">
            Inferred structure
        </div>
        <div class="card-body p-1">
            <@showPayloadStructure structure=structure/>
        </div>
    </div>

</#macro>


