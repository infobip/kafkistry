<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<div style="display: none;">
    <div id="existing_owners">
        <#list existingValues.owners as owner>
            <span class="existing-element">${owner}</span>
        </#list>
    </div>
    <div id="existing_users">
        <#list existingValues.users as user>
            <span class="existing-element">${user}</span>
        </#list>
    </div>
    <div id="existing_producers">
        <#list existingValues.producers as producer>
            <span class="existing-element">${producer}</span>
        </#list>
    </div>
    <div id="existing_topics">
        <#list existingValues.topics as topicName>
            <span class="existing-element">${topicName}</span>
        </#list>
    </div>
    <div id="existing_consumer_groups">
        <#list existingValues.consumerGroups as consumerGroup>
            <span class="existing-element">${consumerGroup}</span>
        </#list>
    </div>
    <div id="existing_topic_field_classifications">
        <#list existingValues.fieldClassifications as fieldClassification>
            <span class="existing-element">${fieldClassification}</span>
        </#list>
    </div>
</div>