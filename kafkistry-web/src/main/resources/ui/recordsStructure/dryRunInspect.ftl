<#-- @ftlvariable name="recordsStructure" type="com.infobip.kafkistry.model.RecordsStructure" -->

<#import "structureElements.ftl" as structure>

<#if !recordsStructure??>
    <div class="alert alert-danger">
        Not having analyzed structure
    </div>
<#else>
    <@structure.showStructure structure=recordsStructure/>
    <#--spacer for collapse not caausing scroll-->
    <div style="height: 80%;"></div>
</#if>
