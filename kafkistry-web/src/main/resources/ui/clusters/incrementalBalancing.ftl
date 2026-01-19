<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="balanceStatus"  type="com.infobip.kafkistry.service.generator.balance.ClusterBalanceStatus" -->
<#-- @ftlvariable name="topicNames" type="java.util.List<java.lang.String>" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Cluster balance</title>
    <meta name="cluster-identifier" content="${clusterIdentifier}">
    <script src="static/cluster/incrementalBalance.js?ver=${lastCommit}"></script>
    <script src="static/regexInspector.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/replicationThrottle.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/assignmentsData.js?ver=${lastCommit}"></script>
    <script src="static/topic/management/reAssignTopicPartitionReplicas.js?ver=${lastCommit}"></script>
    <meta name="current-nav" content="nav-clusters"/>
</head>

<body>


<#include "../commonMenu.ftl">

<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h3><#include "../common/backBtn.ftl"> Cluster resource balance</h3>
    <br/>
    <#include "doc/incrementalBalanceDoc.ftl">
    <table class="table table-hover fixed-layout mt-2">
        <tr>
            <th>Cluster identifier</th>
            <td>
                <a href="${appUrl.clusters().showCluster(clusterIdentifier)}">${clusterIdentifier}</a>
            </td>
        </tr>
    </table>
    <hr/>

    <div class="form">
        <div class="row g-2 form-group">
            <label class="col-auto">Topic(s) name filter:</label>
            <div class="col">
                <div class="input-group">
                    <select class="form-control" name="patternFilterType" title="Include/Exclude" data-style="alert-secondary">
                        <option value="INCLUDE" data-content="<span class='text-success'>Include</span>" selected></option>
                        <option value="EXCLUDE" data-content="<span class='text-danger'>Exclude</span>"></option>
                    </select>
                    <input class="form-control" name="topicNamePattern" type="text"
                           placeholder="enter optional /pattern/..." title="Topic name regex">
                </div>
            </div>
        </div>
        <div class="row g-2">
            <div class="form-group col-3">
                <label for="balance-objective">
                    Balancing objective(s)
                    <#assign objectiveTooltip>
                        Which load dis-balance metrics to score more while finding assignment change
                    </#assign>
                    <@info.icon tooltip = objectiveTooltip/>
                </label>
                <select id="balance-objective" name="balance-priority" class="form-control" multiple="multiple" data-title="ALL">
                    <option value="SIZE">SIZE: disk usage</option>
                    <option value="RATE">RATE: producing input</option>
                    <option value="CONSUMERS_RATE">CONSUMERS_RATE: consumers output</option>
                    <option value="REPLICATION_RATE">REPLICATION_RATE: followers output</option>
                    <option value="REPLICAS_COUNT">REPLICAS_COUNT: topic partitions count per broker</option>
                    <option value="LEADERS_COUNT">LEADERS_COUNT: partition leaders count per broker</option>
                </select>
            </div>
            <div class="form-group col-3">
                <label for="max-iterations">
                    Incremental iterations
                    <#assign iterationsTooltip>
                        How many times to repeat finding next best change in assignments
                    </#assign>
                    <@info.icon tooltip = iterationsTooltip/>
                </label>
                <input id="max-iterations" name="max-iterations" class="form-control" type="number" value="1">
            </div>
            <div class="form-group col-3">
                <label for="analyze-timeout-total">
                    Analyze timeout (sec)
                    <#assign timeoutTooltip>
                        Total time for all iterations. Timeout per iteration is: <br/>
                        <code>total_timeout / num_iterations</code>
                    </#assign>
                    <@info.icon tooltip = timeoutTooltip/>
                </label>
                <input id="analyze-timeout-total" name="analyze-timeout-total" class="form-control" type="number" value="5">
            </div>
            <div class="form-group col-3">
                <label for="max-migration-size">
                    Max migration size
                    <#assign maxMigrationTooltip>
                        Limit so that all proposed migrations won't add up to more than this limit
                    </#assign>
                    <@info.icon tooltip = maxMigrationTooltip/>
                </label>
                <div class="input-group">
                    <input id="max-migration-size" name="max-migration-size" class="form-control" type="number" value=""
                        placeholder="limit or blank">
                    <select class="form-control alert-secondary" name="max-migration-size-unit" title="size unit">
                        <option>GB</option>
                        <option selected>MB</option>
                        <option>kB</option>
                        <option>B</option>
                    </select>
                </div>
            </div>
        </div>
        <div class="row g-2">
            <button id="incremental-migrations-btn" class="btn btn-sm btn-primary width-full">
                Suggest incremental migration(s)
            </button>
        </div>
    </div>
    <br/>

    <#assign statusId = "suggest-migrations">
    <#include "../common/serverOpStatus.ftl">
    <div id="migrations-container"></div>

    <div id="action-buttons-container" style="display: none;">
        <button id="re-assign-migrations-btn" class="btn btn-sm btn-info" data-cluster-identifier="${clusterIdentifier}">
            Re-assign suggested migrations
        </button>
        <#include "../common/cancelBtn.ftl">
    </div>
    <#assign statusId = "">
    <#include "../common/serverOpStatus.ftl">

    <div id="topic-names" style="display: none;">
        <#list topicNames as topic>
            <div class="topic-name" data-topic-name="${topic}"></div>
        </#list>
    </div>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
