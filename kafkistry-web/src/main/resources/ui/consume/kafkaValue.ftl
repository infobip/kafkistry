<#-- @ftlvariable name="kafkaValue" type="com.infobip.kafkistry.service.consume.KafkaValue" -->
<div class="form-row kafka-value-container"
     <#if kafkaValue.rawBase64Bytes??>data-base64='${kafkaValue.rawBase64Bytes}'</#if>>
    <#if kafkaValue.isNull()>
        <div class="col">
            <div class="record-value value-null" data-type="NULL">null</div>
        </div>
    <#elseif kafkaValue.isEmpty()>
        <div class="col">
            <div class="record-value value-empty" data-type="EMPTY"><i class="small">(empty)</i></div>
        </div>
    <#elseif kafkaValue.deserializations?size == 0>
        <div class="col">
            <div class="record-value value-failed" data-type="FAILED"><span class="small font-weight-bold">[failed deserialization]</span></div>
        </div>
    <#else>
        <#list kafkaValue.deserializations as typeTag, deserialization>
            <#assign tagSuffix = deserialization.masked?then(" (MASKED)", "")>
            <div class="col">
                <#switch typeTag>
                    <#case "BYTES">
                        <div class="record-value value-base64" data-type="${typeTag}${tagSuffix}"
                             data-base64='${kafkaValue.rawBase64Bytes}'></div>
                        <#break>
                    <#case "STRING">
                        <div class="record-value value-string" data-type="${typeTag}${tagSuffix}"
                             data-string='${deserialization.value}'></div>
                        <#break>
                    <#default>
                        <div class="record-value value-json" data-type="${typeTag}${tagSuffix}"
                             data-json='${deserialization.asJson}'></div>
                </#switch>
            </div>
        </#list>
    </#if>
</div>
