<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="assignments" type="java.util.Map<java.lang.Integer, java.util.List<java.lang.Integer>>" -->
<#list assignments as partitionId, brokerIds>
    <div id="assignment-data-${topicName}" class="data topic-assignment-data" data-topic-name="${topicName}" hidden="hidden">
        <div class="partition">
            <div class="partition-id">${partitionId?c}</div>
            <#list brokerIds as brokerId>
                <div class="broker-id">${brokerId?c}</div>
            </#list>
        </div>
    </div>
</#list>