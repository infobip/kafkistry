<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<#macro selectLocation selectedIdentifier, selectedTag>
    <label>
        <select name="overrideWhere" class="form-control" data-live-search="true" data-size="8">
            <option disabled selected hidden>Select where...</option>
            <optgroup label="Clusters with tag">
                <#list existingValues.tagClusters as tag, clusters>
                    <#assign display>
                        <span title='Clusters (${clusters?size}): ${clusters?join(",")}'>
                            <span class='badge badge-dark'>TAG</span> ${tag}
                        </span>
                    </#assign>
                    <option value="${tag}" data-type="TAG" data-content="${display}"
                            <#if tag == selectedTag>selected</#if>></option>
                </#list>
            </optgroup>
            <optgroup label="Individual clusters">
                <#list existingValues.clusterRefs as clusterRef>
                    <#assign cId = clusterRef.identifier>
                    <#assign tags = clusterRef.tags>
                    <#assign display>
                        <span title='Tags (${tags?size}): ${tags?join(",")}'>
                            <span class='badge badge-dark'>CLUSTER</span> ${cId}
                        </span>
                    </#assign>
                    <option value="${cId}" data-type="CLUSTER" data-content="${display}"
                            <#if cId == selectedIdentifier>selected</#if>>${cId}</option>
                </#list>
            </optgroup>
        </select>
    </label>
</#macro>

