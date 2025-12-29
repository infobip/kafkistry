<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="result" type="com.infobip.kafkistry.service.produce.ProduceResult" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->

<#if result.success>
    <div class="alert alert-success" role="alert">
        <h4 class="alert-heading"><span class="fas fa-check-circle"></span> Success!</h4>
        <p>Record produced successfully to topic: <strong>${result.topic}</strong></p>
        <hr>
        <table class="table table-sm">
            <tr>
                <th>Partition:</th>
                <td>${result.partition}</td>
            </tr>
            <tr>
                <th>Offset:</th>
                <td>${result.offset}</td>
            </tr>
            <tr>
                <th>Timestamp:</th>
                <td>
                    ${result.timestamp}
                    <#if result.timestamp gt 0>
                        <span class="small time" data-time="${result.timestamp?c}"></span>
                    </#if>
                </td>
            </tr>
        </table>
        <a href="${appUrl.consumeRecords().showConsumePageForProduced(topicName, clusterIdentifier, result.partition, result.offset)}"
           class="btn btn-secondary">
            <span class="fas fa-download"></span> Consume from Topic
        </a>
    </div>
<#else>
    <div class="alert alert-danger" role="alert">
        <h4 class="alert-heading"><span class="fas fa-exclamation-triangle"></span> Error!</h4>
        <p>Failed to produce record to topic: <strong>${topicName}</strong></p>
        <hr>
        <p><strong>Error:</strong> <span class="text-monospace">${result.errorMessage!"Unknown error"}</span></p>
    </div>
</#if>
