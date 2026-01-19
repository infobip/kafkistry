<#-- @ftlvariable name="freezeDirectives" type="java.util.List<com.infobip.kafkistry.model.FreezeDirective>" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->

<#macro freezeEntry reason="" partitionCount=false replicationFactor=false configProperties=[]>
    <div class="freeze-directive m-1">
        <div class="row">
            <div class="col">
                <input class="form-control mb-2" name="freezeReason" title="Reason text"
                    placeholder="Enter reason why is this freeze here (links, jira ABC-123...)" value="${reason}"/>

                <div class="row g-2">
                    <div class="col-auto">
                        <label class="form-control">
                            Partition count
                            <input type="checkbox" name="freezePartitionCount" <#if partitionCount>checked</#if>>
                        </label>
                    </div>
                    <div class="col-auto">
                        <label class="form-control">
                            Replication factor
                            <input type="checkbox" name="freezeReplicationFactor" <#if replicationFactor>checked</#if>>
                        </label>
                    </div>
                    <div class="col">
                        <select class="freezeConfigProperties form-control width-full" multiple="multiple"
                                data-live-search="true" data-size="8" data-max-width="1000px"
                                title="Config properties to freeze">
                            <#list existingValues.topicConfigDoc?keys?sort as configKey>
                                <#assign picked = configProperties?seq_contains(configKey)>
                                <option <#if picked>selected</#if>>${configKey}</option>
                            </#list>
                        </select>
                    </div>
                </div>
            </div>
            <label class="col-auto">
                <button class="remove-freeze-directive-btn btn btn-sm btn-outline-danger">x</button>
            </label>
        </div>
        <hr/>
    </div>
</#macro>

<div class="freeze-directives">
    <#list freezeDirectives as freezeDirective>
        <@freezeEntry
            reason=freezeDirective.reasonMessage
            partitionCount=freezeDirective.partitionCount
            replicationFactor=freezeDirective.replicationFactor
            configProperties=freezeDirective.configProperties
        />
    </#list>
</div>
<button id="add-freeze-directive-btn" class="btn btn-outline-primary float-end">
    Add freeze directive for config/properties
</button>

<div id="freeze-directive-template" class="template" style="display: none;">
    <@freezeEntry/>
</div>
