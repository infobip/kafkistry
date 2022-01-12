<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="enums"  type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, ? extends java.lang.Object>>" -->

<#-- @ftlvariable name="presence" type="com.infobip.kafkistry.model.Presence" -->

<#import "infoIcon.ftl" as info>
<#import "documentation.ftl" as doc>

<div class="presence">
    <div class="form-row">
        <div class="col form-inline">
            <#assign presenceType = (presence.type.name())!"ALL_CLUSTERS">
            <label class="btn btn-outline-primary form-control m-1 <#if presenceType == 'ALL_CLUSTERS'>active</#if>">
                <input type="radio" name="presenceType" value="ALL_CLUSTERS"
                       <#if presenceType == 'ALL_CLUSTERS'>checked</#if>>
                On all clusters <@info.icon tooltip=doc.allClustersPresenceOption/>
            </label>
            <label class="btn btn-outline-success form-control m-1 <#if presenceType == 'INCLUDED_CLUSTERS'>active</#if>">
                <input type="radio" name="presenceType" value="INCLUDED_CLUSTERS"
                       <#if presenceType == 'INCLUDED_CLUSTERS'>checked</#if>>
                Only on <@info.icon tooltip=doc.onlyOnClustersPresenceOption/>
            </label>
            <label class="btn btn-outline-danger form-control m-1 <#if presenceType == 'EXCLUDED_CLUSTERS'>active</#if>">
                <input type="radio" name="presenceType" value="EXCLUDED_CLUSTERS"
                       <#if presenceType == 'EXCLUDED_CLUSTERS'>checked</#if>>
                Not on <@info.icon tooltip=doc.notOnClustersPresenceOption/>
            </label>
            <label class="btn btn-outline-info form-control m-1 <#if presenceType == 'TAGGED_CLUSTERS'>active</#if>">
                <input type="radio" name="presenceType" value="TAGGED_CLUSTERS"
                       <#if presenceType == 'TAGGED_CLUSTERS'>checked</#if>>
                Cluster tag <@info.icon tooltip=doc.taggedClustersPresenceOption/>
            </label>
        </div>
    </div>
    <div class="form-row pt-1 selected-clusters"
         <#if presenceType == 'ALL_CLUSTERS' || presenceType == 'TAGGED_CLUSTERS'>style="display: none;"</#if>>
        <#assign selectedClusters = (presence.kafkaClusterIdentifiers)![]>
        <select name="selectedClusters" multiple title="Select cluster(s)..." class="float-left form-control"
                data-live-search="true" data-size="8">
            <#list existingValues.clusterRefs as clusterRef>
                <#assign cluster = clusterRef.identifier>
                <#assign tags = clusterRef.tags>
                <#assign display>
                    <span title='Tags (${tags?size}): ${tags?join(",")}'>${cluster}</span>
                </#assign>
                <option <#if selectedClusters?seq_contains(cluster)>selected</#if>
                        value="${cluster}" title="${cluster}" data-content="${display}"></option>
            </#list>
        </select>
    </div>
    <div class="form-row pt-1 selected-tag" <#if presenceType != 'TAGGED_CLUSTERS'>style="display: none;"</#if>>
        <#assign selectedTag = (presence.tag)!''>
        <#assign noTags = existingValues.tagClusters?size == 0>
        <select name="selectedTag" title="Select tag..." class="float-left form-control"
                data-live-search="true" data-size="8">
            <#list existingValues.tagClusters as tag, clusters>
                <#assign display>
                    <span title='Clusters (${clusters?size}): ${clusters?join(",")}'>
                        <span class='badge badge-secondary'>${tag}</span>
                    </span>
                </#assign>
                <option <#if selectedTag == tag>selected</#if>
                        value="${tag}" title="${tag}" data-content="${display}"></option>
            </#list>
        </select>
        <#if noTags>
            <small><i>(no tags exist on cluster(s))</i></small>
        </#if>
    </div>
</div>
