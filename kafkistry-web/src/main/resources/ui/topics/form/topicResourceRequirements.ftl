<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="resourceRequirements" type="com.infobip.kafkistry.model.ResourceRequirements" -->

<#import "topicFormConfigComponents.ftl" as comp>

<#macro msgRate rate, rateFactor, rateUnit>
    <input type="number" name="messagesRateAmount"
           placeholder="Amount of messages sent to the topic" class="form-control"
           value="${rate}" title="message rate quantity"/>
    <select name="messagesRateFactor" class="bg-body-secondary form-select" style="max-width: 8rem;" title="unit multiplier">
        <option value="ONE" <#if rateFactor=="ONE">selected</#if>>msg</option>
        <option value="K" <#if rateFactor=="K">selected</#if>>1K msg</option>
        <option value="M" <#if rateFactor=="M">selected</#if>>1M msg</option>
        <option value="G" <#if rateFactor=="G">selected</#if>>1G msg</option>
    </select>
    <select name="messagesRateUnit" class="bg-body-secondary form-select" style="max-width: 7rem;" title="per time unit">
        <option value="MSG_PER_DAY" <#if rateUnit=="MSG_PER_DAY">selected</#if>>/ day</option>
        <option value="MSG_PER_HOUR" <#if rateUnit=="MSG_PER_HOUR">selected</#if>>/ hour</option>
        <option value="MSG_PER_MINUTE" <#if rateUnit=="MSG_PER_MINUTE">selected</#if>>/ minute</option>
        <option value="MSG_PER_SECOND" <#if rateUnit=="MSG_PER_SECOND">selected</#if>>/ second</option>
    </select>
</#macro>

<#macro msgRateOverride cluster tag rate rateFactor rateUnit>
    <div class="messages-rate-override form-group width-full mt-1">
        <div class="input-group">
            <@comp.commonLocComp.selectLocation selectedIdentifier=cluster selectedTag=tag />
            <@msgRate rate=rate rateFactor=rateFactor rateUnit=rateUnit />
            <button role="button" class="ml-1 p-2 remove-messages-rate-override-btn btn btn-sm btn-outline-danger">x</button>
        </div>
    </div>
</#macro>

<#macro retention durationAmount retentionUnit>
    <input type="number" name="retentionAmount" placeholder="After how much time the data will be deleted"
           class="form-control" value="${durationAmount}" title="retention time quantity"/>
    <select name="retentionUnit" class="bg-body-secondary form-select" style="max-width: 8rem;" title="time unit">
        <option value="DAYS" <#if retentionUnit=="DAYS">selected</#if>>day(s)</option>
        <option value="HOURS" <#if retentionUnit=="HOURS">selected</#if>>hour(s)</option>
        <option value="MINUTES" <#if retentionUnit=="MINUTES">selected</#if>>minute(s)</option>
        <option value="SECONDS" <#if retentionUnit=="SECONDS">selected</#if>>second(s)</option>
    </select>
</#macro>

<#macro retentionOverride cluster tag durationAmount retentionUnit>
    <div class="data-retention-override form-group width-full mt-1">
        <div class="input-group d-flex align-items-center">
            <@comp.commonLocComp.selectLocation selectedIdentifier=cluster selectedTag=tag />
            <@retention durationAmount=durationAmount retentionUnit=retentionUnit />
            <button role="button" class="ml-1 p-2 remove-data-retention-override-btn btn btn-sm btn-outline-danger">x</button>
        </div>
    </div>
</#macro>

<div class="resource-input-row form-group row mb-3">
    <label class="col-sm-2 col-form-label">Messages rate</label>

    <div class="col-sm-10">
        <div class="requirements-message-rate messagesRateDefault row">
            <div class="input-group d-flex align-items-center">
                <#assign rate = (resourceRequirements.messagesRate.amount?c)!''>
                <#assign factor = (resourceRequirements.messagesRate.factor.name())!'K'>
                <#assign rateUnit = (resourceRequirements.messagesRate.unit.name())!''>
                <span class="form-control bg-body-secondary">All clusters</span>
                <@msgRate rate=rate rateFactor=factor rateUnit=rateUnit />
                <button id="add-messages-rate-override-btn" role="button" class="btn btn-outline-primary ml-1">
                    Add override
                </button>
            </div>
        </div>
        <div class="requirements-message-rate messagesRateOverrides row">
            <#if resourceRequirements??>
                <#list resourceRequirements.messagesRateTagOverrides as tag, messagesRate>
                    <@msgRateOverride cluster="" tag=tag
                    rate=messagesRate.amount?c rateFactor=messagesRate.factor.name() rateUnit=messagesRate.unit.name()/>
                </#list>
                <#list resourceRequirements.messagesRateOverrides as clusterIdentifier, messagesRate>
                    <@msgRateOverride cluster=clusterIdentifier tag=""
                    rate=messagesRate.amount?c rateFactor=messagesRate.factor.name() rateUnit=messagesRate.unit.name()/>
                </#list>
            </#if>
        </div>
        <div class="messages-rate-override-template" style="display: none;">
            <@msgRateOverride cluster="" tag="" rate="" rateFactor="ONE" rateUnit="MSG_PER_DAY"/>
        </div>
    </div>
</div>

<div class="resource-input-row form-group row mb-3">
    <label class="col-sm-2 col-form-label">Avg msg size</label>
    <div class="col-sm-3">
        <div class="input-group">
            <input class="form-control" type="number" name="avgMessageSize" title="Size quantity"
                   value="${(resourceRequirements.avgMessageSize.amount?c)!''}"/>
            <#assign unit = (resourceRequirements.avgMessageSize.unit.name())!''>
            <select name="avgMessageSizeUnit" class="form-control bg-body-secondary" title="Size unit">
                <option value="B" <#if unit=="B">selected</#if>>bytes</option>
                <option value="KB" <#if unit=="KB">selected</#if>>kB</option>
                <option value="MB" <#if unit=="MB">selected</#if>>MB</option>
            </select>
        </div>
    </div>
</div>

<div class="resource-input-row form-group row mb-3">
    <label class="col-sm-2 col-form-label">Retention</label>

    <div class="col-sm-10">
        <div class="requirements-data-retention dataRetentionDefault row">
            <div class="input-group">
                <#assign retentionAmount = (resourceRequirements.retention.amount?c)!''>
                <#assign retentionUnit = (resourceRequirements.retention.unit.name())!'DAYS'>
                <span class="form-control bg-body-secondary">All clusters</span>
                <@retention durationAmount=retentionAmount retentionUnit=retentionUnit />
                <button id="add-retention-override-btn" role="button" class="btn btn-outline-primary ml-1">
                    Add override
                </button>
            </div>
        </div>
        <div class="requirements-data-retention dataRetentionOverrides row">
            <#if resourceRequirements??>
                <#list resourceRequirements.retentionTagOverrides as tag, dataRetention>
                    <@retentionOverride cluster="" tag=tag
                    durationAmount=dataRetention.amount?c retentionUnit=dataRetention.unit.name()/>
                </#list>
                <#list resourceRequirements.retentionOverrides as clusterIdentifier, dataRetention>
                    <@retentionOverride cluster=clusterIdentifier tag=""
                    durationAmount=dataRetention.amount?c retentionUnit=dataRetention.unit.name()/>
                </#list>
            </#if>
        </div>
        <div class="data-retention-override-template" style="display: none;">
            <@retentionOverride cluster="" tag="" durationAmount="" retentionUnit="DAYS"/>
        </div>
    </div>

</div>
