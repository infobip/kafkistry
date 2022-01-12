<#-- @ftlvariable name="dataMigration" type="com.infobip.kafkistry.service.DataMigration" -->
<#import "../common/util.ftl" as __util>

<tr>
    <th>Total bytes all brokers</th>
    <td class="p-0">
        <table class="table table-sm">
            <thead class="thead-light">
            <tr>
                <th>Transfer IO</th>
                <th>Total add</th>
                <th>Total release</th>
                <th>Re-assigned partitions</th>
            </tr>
            </thead>
            <tr>
                <td>${__util.prettyDataSize(dataMigration.totalIOBytes)}</td>
                <td>${__util.prettyDataSize(dataMigration.totalAddBytes)}</td>
                <td>${__util.prettyDataSize(dataMigration.totalReleaseBytes)}</td>
                <td>${dataMigration.reAssignedPartitions}</td>
            </tr>
        </table>
</tr>
<tr class="no-hover">
    <th>Bytes per broker</th>
    <td class="p-0">
        <table class="table table-sm m-0">
            <thead class="thead-light">
            <tr>
                <th>Broker</th>
                <th>Total IO</th>
                <th>In IO/Add</th>
                <th>Out IO</th>
                <th>Release</th>
            </tr>
            </thead>
            <#assign involvedBrokers = []>
            <#list dataMigration.perBrokerTotalIOBytes?keys as broker>
                <#if !involvedBrokers?seq_contains(broker)><#assign involvedBrokers = involvedBrokers + [broker]></#if>
            </#list>
            <#list dataMigration.perBrokerReleasedBytes?keys as broker>
                <#if !involvedBrokers?seq_contains(broker)><#assign involvedBrokers = involvedBrokers + [broker]></#if>
            </#list>
            <#list involvedBrokers?sort as broker>
                <tr>
                    <th>${broker}</th>
                    <td>
                        <#if dataMigration.perBrokerTotalIOBytes?api.containsKey(broker)>
                            ${__util.prettyDataSize(dataMigration.perBrokerTotalIOBytes?api.get(broker))}
                        <#else>
                            ---
                        </#if>
                    <td>
                        <#if dataMigration.perBrokerInputBytes?api.containsKey(broker)>
                            ${__util.prettyDataSize(dataMigration.perBrokerInputBytes?api.get(broker))}
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if dataMigration.perBrokerOutputBytes?api.containsKey(broker)>
                            ${__util.prettyDataSize(dataMigration.perBrokerOutputBytes?api.get(broker))}
                        <#else>
                            ---
                        </#if>
                    </td>
                    <td>
                        <#if dataMigration.perBrokerReleasedBytes?api.containsKey(broker)>
                            ${__util.prettyDataSize(dataMigration.perBrokerReleasedBytes?api.get(broker))}
                        <#else>
                            ---
                        </#if>
                    </td>
                </tr>
            </#list>
            <#if involvedBrokers?size == 0>
                <tr>
                    <td colspan="100"><i>(none)</i></td>
                </tr>
            </#if>
        </table>
    </td>
</tr>
