<#-- @ftlvariable name="kafkaValue" type="com.infobip.kafkistry.service.consume.KafkaValue" -->
<div class="row g-2 kafka-value-container text-break"
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
            <#assign typeGutter = "${typeTag}${tagSuffix}">
            <div class="col <#if deserialization.suppressed>suppresed</#if> <#if deserialization.masked>masked</#if> <#if deserialization.transformed>transformed</#if>"
                 <#if deserialization.suppressed>style="display: none;" </#if>
            >
                <div class="kafka-value-guttered">
                <#assign copyValue = "">
                <#switch typeTag>
                    <#case "BYTES">
                        <div class="record-value value-base64" data-type="${typeTag}${tagSuffix}"
                             data-base64='${kafkaValue.rawBase64Bytes}'></div>
                        <#assign copyValue = kafkaValue.rawBase64Bytes>
                        <#break>
                    <#case "STRING">
                        <div class="record-value value-string" data-type="${typeTag}${tagSuffix}"
                             data-string='${deserialization.value?url}'></div>
                        <#assign copyValue = deserialization.value>
                        <#break>
                    <#default>
                        <div class="record-value value-json" data-type="${typeTag}${tagSuffix}"
                             data-json='${deserialization.asJson?url}'></div>
                        <#assign copyValue = deserialization.asJson>
                </#switch>
                <div class="kafka-value-gutter">
                    <div class="d-inline-block align-top">
                        <span>${typeTag}${tagSuffix}</span>
                    </div>
                    <div class="btn btn-xsmall btn-secondary kafka-value-copy-btn position-relative" title="Copy to clipboard">
                        &nbsp;⧉&nbsp;
                    </div>
                    <#if deserialization.isTransformed()>
                        <div class="btn btn-xsmall btn-secondary kafka-value-toggle-suppressed-btn" title="Show/hide original value">
                            &nbsp;↩&nbsp;
                        </div>
                    </#if>
                    <input name="kafkaCopyValue" type="text" value="${copyValue}" style="display: none;" title="copy input">
                </div>
                </div>
            </div>
        </#list>
    </#if>
</div>
