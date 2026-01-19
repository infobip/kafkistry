
<#-- @ftlvariable name="config"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.kafka.ConfigValue>" -->
<#-- @ftlvariable name="configEntryStatuses"  type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.ValueInspection>" -->
<#-- @ftlvariable name="expectedConfig"  type="java.util.Map<java.lang.String, java.lang.String>" -->
<#-- @ftlvariable name="showExpected"  type="java.lang.Boolean" -->
<#-- @ftlvariable name="topicConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->
<#-- @ftlvariable name="brokerConfigDoc" type="java.util.Map<java.lang.String, java.lang.String>" -->

<#import "infoIcon.ftl" as confInfo>
<#assign showExpected = showExpected!false >

<style>
    .non-default {
        --bs-table-bg: var(--bs-primary-bg-subtle);
    }
    .non-default .conf-value {
        font-weight: bold;
    }
</style>

<table class="table table-hover table-sm table-bordered table-hover">
    <thead class="table-theme-dark">
    <tr>
        <th>Key</th>
        <th>Value</th>
        <th>Source</th>
        <th>Read-only</th>
        <th>Sensitive</th>
        <#if showExpected>
            <th>Expected by registry</th>
            <th>Expected by cluster</th>
        </#if>
    </tr>
    </thead>
    <tbody>
    <#list config as key, configVal>
        <#assign invalid = (!(configEntryStatuses[key].valid))!false>
        <tr class="${configVal.default?then('default', 'non-default')}">
            <td>
                ${key}
                <#if (topicConfigDoc[key])??>
                    <@confInfo.icon tooltip=topicConfigDoc[key]?replace('"', "'")/>
                <#elseif (brokerConfigDoc[key])??>
                    <@confInfo.icon tooltip=brokerConfigDoc[key]?replace('"', "'")/>
                </#if>
            </td>
            <td class="conf-value <#if invalid>value-mismatch</#if>"
                data-name="${key}" data-value="${configVal.value!''}" style="word-break: break-word;">
                <#if configVal.value??>
                    ${configVal.value}
                <#else>
                    <pre class="text-primary mb-0">null</pre>
                </#if>
            </td>
            <td>
                <span class="small badge bg-secondary">
                    ${configVal.source.name()?replace('_', ' ')?replace('CONFIG', '')}
                </span>
            </td>
            <td>${configVal.readOnly?then('yes', 'no')}</td>
            <td>${configVal.sensitive?then('yes', 'no')}</td>
            <#if showExpected>
                <#if (configEntryStatuses[key]??)>
                    <#assign valueStatus = configEntryStatuses[key]>
                    <#assign value = valueStatus.expectingClusterDefault?then("---", valueStatus.expectedValue!'')>
                    <td class="conf-value <#if invalid && !valueStatus.expectingClusterDefault>value-mismatch</#if>" data-name="${key}" data-value="${value}">
                        <#if value == '' && !(valueStatus.expectedValue)??>
                            <span class="text-primary">null</span>
                        <#else>
                            ${value}
                        </#if>
                    </td>
                    <#assign value = valueStatus.expectingClusterDefault?then(valueStatus.expectedValue!'', "---")>
                    <td class="conf-value <#if invalid && valueStatus.expectingClusterDefault>value-mismatch</#if>" data-name="${key}" data-value="${value}">
                        <#if value == '' && !(valueStatus.expectedValue)??>
                            <span class="text-primary">null</span>
                        <#else>
                            ${value}
                        </#if>
                    </td>
                <#else>
                    <td>---</td>
                    <td>---</td>
                </#if>
            </#if>
        </tr>
    </#list>
    <#if config?size == 0>
        <tr>
            <td colspan="100"><i>(no config values)</i></td>
        </tr>
    </#if>
    </tbody>

</table>
