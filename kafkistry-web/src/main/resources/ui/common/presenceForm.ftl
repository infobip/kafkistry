<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="kafkistryUser"  type="com.infobip.kafkistry.webapp.security.User" -->
<#-- @ftlvariable name="forceTagForPresence" type="java.lang.Boolean" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="enums"  type="java.util.Map<java.lang.String, java.util.Map<java.lang.String, ? extends java.lang.Object>>" -->

<#-- @ftlvariable name="presence" type="com.infobip.kafkistry.model.Presence" -->

<#import "infoIcon.ftl" as info>
<#import "documentation.ftl" as doc>

<#assign tagOnly = forceTagForPresence!false>
<#assign disabledPresenceTypeDoc>
    <#if tagOnly>
        <span class='badge bg-danger'>DISABLED</span>:
        Only tag based selector should be used
        <br/>
    </#if>
</#assign>

<div class="presence">
    <#assign showAdminForceTagBypass = tagOnly && kafkistryUser?? && kafkistryUser.role.name == "ADMIN">
    <#if showAdminForceTagBypass>
        <div class="row g-2 bypass-only-tag-presence-container" style="display: none;">
            <div class="col d-flex align-items-center gap-2">
                <button class="btn btn-sm btn-outline-secpndary bypass-only-tag-presence-btn">
                    &#10004; I'm admin, I don't need "tag-only" restriction, I know what am I doing
                </button>
            </div>
        </div>
    </#if>
    <div class="row g-2">
        <div class="col d-flex align-items-center gap-2">
            <#if showAdminForceTagBypass>
                <label class="btn btn-outline-danger opt-in-bypass-only-tag-presence-btn" title="Bypass tag presence forcing...">
                    &#128293;
                </label>
            </#if>
            <#if (presence.type.name())??>
                <#if presence.type == "INCLUDED_CLUSTERS" && presence.kafkaClusterIdentifiers?size == 0 && tagOnly>
                    <#assign presenceType = "TAGGED_CLUSTERS">
                <#else>
                    <#assign presenceType = presence.type.name()>
                </#if>
            <#else>
                <#assign presenceType = tagOnly?then("TAGGED_CLUSTERS", "ALL_CLUSTERS")>
            </#if>
            <label class="btn btn-outline-primary <#if presenceType == 'ALL_CLUSTERS'>active</#if>
                    <#if tagOnly>disabled</#if>">
                <input type="radio" name="presenceType" value="ALL_CLUSTERS"
                       <#if presenceType == 'ALL_CLUSTERS'>checked</#if> <#if tagOnly>disabled</#if>>
                <#assign tooltip>
                    ${disabledPresenceTypeDoc}
                    <span class='badge bg-secondary'>ON ALL</span>: ${doc.allClustersPresenceOption}
                </#assign>
                On all clusters <@info.icon tooltip=tooltip/>
            </label>
            <label class="btn btn-outline-success <#if presenceType == 'INCLUDED_CLUSTERS'>active</#if>
                   <#if tagOnly>disabled</#if>">
                <input type="radio" name="presenceType" value="INCLUDED_CLUSTERS"
                       <#if presenceType == 'INCLUDED_CLUSTERS'>checked</#if> <#if tagOnly>disabled</#if>>
                <#assign tooltip>
                    ${disabledPresenceTypeDoc}
                    <span class='badge bg-secondary'>ONLY ON</span>: ${doc.onlyOnClustersPresenceOption}
                </#assign>
                Only on <@info.icon tooltip=tooltip/>
            </label>
            <label class="btn btn-outline-danger <#if presenceType == 'EXCLUDED_CLUSTERS'>active</#if>
                   <#if tagOnly>disabled</#if>">
                <input type="radio" name="presenceType" value="EXCLUDED_CLUSTERS"
                       <#if presenceType == 'EXCLUDED_CLUSTERS'>checked</#if> <#if tagOnly>disabled</#if>>
                <#assign tooltip>
                    ${disabledPresenceTypeDoc}
                    <span class='badge bg-secondary'>NOT ON</span>: ${doc.notOnClustersPresenceOption}
                </#assign>
                Not on <@info.icon tooltip=tooltip/>
            </label>
            <label class="btn btn-outline-info <#if presenceType == 'TAGGED_CLUSTERS'>active</#if>">
                <input type="radio" name="presenceType" value="TAGGED_CLUSTERS"
                       <#if presenceType == 'TAGGED_CLUSTERS'>checked</#if>>
                Cluster tag <@info.icon tooltip=doc.taggedClustersPresenceOption/>
            </label>
            <a href="${appUrl.clusters().showTags()}#add-new-tag-btn" target="_blank">
                Need new tag?
            </a>
        </div>
    </div>
    <div class="row g-2 pt-1 selected-clusters"
         <#if presenceType == 'ALL_CLUSTERS' || presenceType == 'TAGGED_CLUSTERS'>style="display: none;"</#if>>
        <#assign selectedClusters = (presence.kafkaClusterIdentifiers)![]>
        <select name="selectedClusters" multiple title="Select cluster(s)..." class="float-start"
                data-live-search="true" data-size="8" <#if tagOnly>disabled</#if>>
            <#list existingValues.clusterRefs as clusterRef>
                <#assign cluster = clusterRef.identifier>
                <#assign tags = clusterRef.tags>
                <#assign tooltip>
                    <strong>Tags</strong> <i>(count=${tags?size})</i>: <br/>
                    ${tags?join(", ")}
                </#assign>
                <#assign display>
                    <span class='value-marker' data-value='${cluster}'>
                        ${cluster}
                    </span>
                </#assign>
                <option <#if selectedClusters?seq_contains(cluster)>selected</#if>
                        value="${cluster}" data-title="${tooltip}" data-content="${display}"></option>
            </#list>
        </select>
    </div>
    <div class="row g-2 pt-1 selected-tag" <#if presenceType != 'TAGGED_CLUSTERS'>style="display: none;"</#if>>
        <#assign selectedTag = (presence.tag)!''>
        <#assign noTags = existingValues.presenceTagClusters?size == 0>
        <select name="selectedTag" title="Select tag..." class="form-control"
                data-live-search="true" data-size="8">
            <#list existingValues.presenceTagClusters as tag, clusters>
                <#assign tooltip>
                    <strong>Clusters</strong> <i>(count=${clusters?size})</i>: <br/>
                    ${clusters?join(",")}
                </#assign>
                <#assign display>
                    <span class='value-marker' data-value='${tag}'>
                        <span class='badge bg-secondary'>${tag}</span>
                    </span>
                </#assign>
                <option <#if selectedTag == tag>selected</#if>
                        value="${tag}" data-title="${tooltip}" data-content="${display}"></option>
            </#list>
        </select>
        <#if noTags>
            <small><i>(no presence tags exist on cluster(s))</i></small>
        </#if>
    </div>
</div>
