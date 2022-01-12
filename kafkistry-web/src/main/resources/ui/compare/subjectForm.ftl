<#-- @ftlvariable name="allTopics" type="java.util.List<java.lang.String>" -->
<#-- @ftlvariable name="allClusters" type="java.util.List<java.lang.String>" -->

<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="compareType" type="com.infobip.kafkistry.service.topic.compare.ComparingSubjectType" -->


<form class="comparing-subject form-inline mb-1 row">
    <div class="col-1 p-1">
        <span class="base-badge width-full badge badge-primary" style="display: none;">
            BASE
        </span>
    </div>
    <button type="button" class="col- btn btn-sm btn-outline-info move-up-btn" title="Move up">↑</button>
    <button type="button" class="col- btn btn-sm btn-outline-info move-down-btn ml-sm-1" title="Move down">↓</button>

    <input type="search" placeholder="Select topic..." name="topic" title="topic name"
           class="col topic-input form-control form-control-sm m-sm-1" value="${topicName!""}">

    <label class="col-">
        <select name="cluster" class="form-control form-control-sm mr-sm-1">
            <option value="" disabled <#if !(clusterIdentifier??)>selected</#if>>Select cluster...</option>
            <#list allClusters as cluster>
                <option value="${cluster}" <#if clusterIdentifier?? && clusterIdentifier==cluster>selected</#if>>${cluster}</option>
            </#list>
        </select>
    </label>

    <label class="col- mr-sm-1">
        <label class="btn btn-sm btn-outline-primary mr-sm-1">
            <input type="radio" name="type" value="CONFIGURED" <#if !(compareType??) || compareType.name()=="CONFIGURED">checked</#if>/>
            <span class="ml-1">Configured</span>
        </label>
        <label class="btn btn-sm btn-outline-primary mr-sm-1">
            <input type="radio" name="type" value="ACTUAL" <#if (compareType??) && compareType.name()=="ACTUAL">checked</#if>/>
            <span class="ml-1">Actual</span>
        </label>
    </label>

    <button type="button" class="col- remove-subject-btn btn btn-sm btn-outline-danger">x</button>

</form>
