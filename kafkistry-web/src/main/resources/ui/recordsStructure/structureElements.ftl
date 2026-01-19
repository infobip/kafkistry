<#-- @ftlvariable name="json" type="com.fasterxml.jackson.databind.ObjectMapper" -->

<#import "../common/infoIcon.ftl" as info>

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
            <span class="badge bg-danger">HIGH CARDINALITY</span>
        <#elseif fieldValue.tooBig>
            <span class="badge bg-danger">TOO BIG</span>
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
    <span class="field-type-tag field-type-${field.type} badge bg-secondary" title="Field type">
        ${field.type}
    </span>
    <#if fullName>
        <#if (field.fullName)??>
            <code class="ml-2" title="Full field name">${field.fullName}</code>
        <#else>
            <span class="badge bg-info" title="Full name = null">ROOT</span>
        </#if>
        <#if field.nullable>
            <span class="badge bg-neutral">NULLABLE</span>
        </#if>
    <#else>
        <#if field.nullable>
            <span class="badge bg-neutral">NULLABLE</span>
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
                <button class="btn btn-sm btn-primary" type="button" data-bs-toggle="collapse"
                        data-bs-target="#${collapseTarget}">
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


<#macro showSizeStat stat>
<#-- @ftlvariable name="stat" type="com.infobip.kafkistry.model.SizeStatistic" -->
    <#assign tooltip>
        <table class='table table-sm m-0'>
            <tr><th># samples</th><td>${stat.count}</td></tr>
            <tr><th>Avg</th><td>${stat.avg} bytes</td></tr>
            <tr><th>Min</th><td>${stat.min} bytes</td></tr>
            <tr><th>Max</th><td>${stat.max} bytes</td></tr>
        </table>
    </#assign>
    ${stat.avg} <@info.icon tooltip=tooltip/>
</#macro>

<#macro showRecordSize size>
<#-- @ftlvariable name="size" type="com.infobip.kafkistry.model.RecordSize" -->
    <table class="table table-hover m-0">
        <thead class="table-theme-dark">
        <tr>
            <th></th>
            <th>15 Min</th>
            <th>1 Hour</th>
            <th>6 Hours</th>
            <th>Day</th>
            <th>Week</th>
            <th>Month</th>
        </tr>
        </thead>
        <tr>
            <th>Msg Total</th>
            <td><#if (size.msg.last15Min)??><@showSizeStat stat=size.msg.last15Min/><#else>---</#if></td>
            <td><#if (size.msg.lastHour)??><@showSizeStat stat=size.msg.lastHour/><#else>---</#if></td>
            <td><#if (size.msg.last6Hours)??><@showSizeStat stat=size.msg.last6Hours/><#else>---</#if></td>
            <td><#if (size.msg.lastDay)??><@showSizeStat stat=size.msg.lastDay/><#else>---</#if></td>
            <td><#if (size.msg.lastWeek)??><@showSizeStat stat=size.msg.lastWeek/><#else>---</#if></td>
            <td><#if (size.msg.lastMonth)??><@showSizeStat stat=size.msg.lastMonth/><#else>---</#if></td>
        </tr>
        <tr>
            <th>Key</th>
            <td><#if (size.key.last15Min)??><@showSizeStat stat=size.key.last15Min/><#else>---</#if></td>
            <td><#if (size.key.lastHour)??><@showSizeStat stat=size.key.lastHour/><#else>---</#if></td>
            <td><#if (size.key.last6Hours)??><@showSizeStat stat=size.key.last6Hours/><#else>---</#if></td>
            <td><#if (size.key.lastDay)??><@showSizeStat stat=size.key.lastDay/><#else>---</#if></td>
            <td><#if (size.key.lastWeek)??><@showSizeStat stat=size.key.lastWeek/><#else>---</#if></td>
            <td><#if (size.key.lastMonth)??><@showSizeStat stat=size.key.lastMonth/><#else>---</#if></td>
        </tr>
        <tr>
            <th>Value</th>
            <td><#if (size.value.last15Min)??><@showSizeStat stat=size.value.last15Min/><#else>---</#if></td>
            <td><#if (size.value.lastHour)??><@showSizeStat stat=size.value.lastHour/><#else>---</#if></td>
            <td><#if (size.value.last6Hours)??><@showSizeStat stat=size.value.last6Hours/><#else>---</#if></td>
            <td><#if (size.value.lastDay)??><@showSizeStat stat=size.value.lastDay/><#else>---</#if></td>
            <td><#if (size.value.lastWeek)??><@showSizeStat stat=size.value.lastWeek/><#else>---</#if></td>
            <td><#if (size.value.lastMonth)??><@showSizeStat stat=size.value.lastMonth/><#else>---</#if></td>
        </tr>
        <tr>
            <th>Headers</th>
            <td><#if (size.headers.last15Min)??><@showSizeStat stat=size.headers.last15Min/><#else>---</#if></td>
            <td><#if (size.headers.lastHour)??><@showSizeStat stat=size.headers.lastHour/><#else>---</#if></td>
            <td><#if (size.headers.last6Hours)??><@showSizeStat stat=size.headers.last6Hours/><#else>---</#if></td>
            <td><#if (size.headers.lastDay)??><@showSizeStat stat=size.headers.lastDay/><#else>---</#if></td>
            <td><#if (size.headers.lastWeek)??><@showSizeStat stat=size.headers.lastWeek/><#else>---</#if></td>
            <td><#if (size.headers.lastMonth)??><@showSizeStat stat=size.headers.lastMonth/><#else>---</#if></td>
        </tr>
    </table>
</#macro>

<#macro showPayloadStructure structure>
<#-- @ftlvariable name="structure" type="com.infobip.kafkistry.model.RecordsStructure" -->
    <div>
        Record root structure type: <span class="badge bg-secondary">${structure.payloadType}</span>
        <#if structure.nullable>
            <span class="badge bg-neutral">NULLABLE</span>
        </#if>
        <#if (structure.jsonFields)??>
            <div>
                <label class="btn btn-sm btn-outline-secondary">
                    List view
                    <input type="radio" name="fields-display-view-type" value="list" checked>
                </label>
                <label class="btn btn-sm btn-outline-secondary">
                    Tree view
                    <input type="radio" name="fields-display-view-type" value="tree">
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
        <div class="card-header h5">Sizes</div>
        <div class="card-body p-0">
            <@showRecordSize size=structure.size/>
        </div>
    </div>
    <br/>

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


