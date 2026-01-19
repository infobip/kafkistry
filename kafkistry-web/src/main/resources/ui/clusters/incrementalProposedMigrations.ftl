<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="clusterIdentifier"  type="java.lang.String" -->
<#-- @ftlvariable name="proposedMigrations"  type="com.infobip.kafkistry.service.generator.balance.ProposedMigrations" -->

<#import "../common/util.ftl" as util>

<#if proposedMigrations.migrations?size == 0>
    <div class="alert alert-warning">
        Did not find topic replicas candidates to migrate
    </div>
    <ul>
        <li>Cluster might already be balanced enough so that any migration would cause bigger disbalance</li>
        <li>Max migration size limit might be to restrictive</li>
        <li>Analyze timeout too low</li>
    </ul>
<#else>
    <div class="alert alert-success">
        Suggested ${proposedMigrations.migrations?size} topic replica(s) to migrate
    </div>

    <#macro migrationRow migration>
    <#-- @ftlvariable name="migration"  type="com.infobip.kafkistry.service.generator.balance.TopicPartitionMigration" -->
        <td>
            <a href="${appUrl.topics().showInspectTopicOnCluster(migration.topicPartition.topic, clusterIdentifier)}"
               target="_blank">
                ${migration.topicPartition.topic}
            </a>
            [${migration.topicPartition.partition?c}]
        </td>
        <td>
            <#if migration.fromBrokerIds?size == 0 && migration.toBrokerIds?size == 0>
                ---
            <#else>
                ${migration.fromBrokerIds?join(", ")} &rarr; ${migration.toBrokerIds?join(", ")}
            </#if>
        </td>
        <td>
            <#if migration.oldLeader == migration.newLeader>
                ${migration.newLeader}
            <#else>
                ${migration.oldLeader} &rarr; ${migration.newLeader}
            </#if>
        </td>
        <td>${util.prettyDataSize(migration.load.size)}</td>
        <td>${util.prettyNumber(migration.load.rate)} msg/sec</td>
        <td>${migration.load.consumers}</td>
    </#macro>

    <div class="card">
        <div class="card-header">
            <span class="h4">Topic partition migrations</span>
            <span class="ml-3 migrations-display-options">
                <label class="btn btn-sm btn-outline-primary active">
                    All effective
                    <input type="radio" name="migrations-display-type" value="#effective-migrations" checked>
                </label>
                <label class="btn btn-sm btn-outline-primary">
                    Per iteration
                    <input type="radio" name="migrations-display-type" value="#iteration-migrations">
                </label>
            </span>
        </div>
        <div class="card-body p-0">
            <table id="effective-migrations" class="migrations-table table m-0">
                <thead class="table-theme-dark">
                <tr>
                    <th>Topic partition</th>
                    <th>Migration</th>
                    <th>Leader</th>
                    <th>Disk usage</th>
                    <th>Rate usage</th>
                    <th># Consumers</th>
                </tr>
                </thead>
                <#list proposedMigrations.migrations as migration>
                    <tr>
                        <@migrationRow migration=migration/>
                    </tr>
                </#list>
            </table>
            <table id="iteration-migrations" class="migrations-table table m-0" style="display: none;">
                <thead class="table-theme-dark">
                <tr>
                    <th>Iteration</th>
                    <th>Topic partition</th>
                    <th>Migration</th>
                    <th>Leader</th>
                    <th>Disk usage</th>
                    <th>Rate usage</th>
                    <th># Consumers</th>
                </tr>
                </thead>
                <#list proposedMigrations.iterationMigrations as migrations>
                    <#list migrations as migration>
                        <tr>
                            <#if migration?index == 0>
                                <th rowspan="${migrations?size}">${migrations?index + 1}.</th>
                            </#if>
                            <@migrationRow migration=migration/>
                        </tr>
                    </#list>
                </#list>
            </table>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header">
            <h4>Cluster balance comparison</h4>
        </div>
        <div class="card-body p-0">
            <#assign maxValueClass = "text-primary">
            <#assign minValueClass = "text-info">
            <ul class="list-group list-group-horizontal m-2">
                <li class="list-group-item p-2">
                    <span class="${maxValueClass}">MAX value broker</span>
                </li>
                <li class="list-group-item p-2">
                    <span class="${minValueClass}">MIN value broker</span>
                </li>
            </ul>

            <table class="table table-hover m-0">
                <thead class="table-theme-dark">
                <tr>
                    <th>Broker/Metric</th>
                    <th>When</th>
                    <th>Disk</th>
                    <th>Rate</th>
                    <th>Consume</th>
                    <th>Replication</th>
                    <th>Replicas</th>
                    <th>Leaders</th>
                </tr>
                </thead>
                <#assign statusBefore = proposedMigrations.clusterBalanceBefore>
                <#assign statusAfter = proposedMigrations.clusterBalanceAfter>
                <#list statusBefore.brokerLoads as brokerId, loadBefore>
                    <#assign loadAfter = statusAfter.brokerLoads?api.get(brokerId)>
                    <tr>
                        <th rowspan="3">${brokerId?c}</th>
                        <th><span class="badge bg-secondary">Before</span></th>

                        <#assign maxClass = statusBefore.maxLoadBrokers.size?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.size?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyDataSize(loadBefore.size)}</td>

                        <#assign maxClass = statusBefore.maxLoadBrokers.rate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.rate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadBefore.rate)} msg/sec</td>

                        <#assign maxClass = statusBefore.maxLoadBrokers.consumeRate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.consumeRate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadBefore.consumeRate)} msg/sec</td>

                        <#assign maxClass = statusBefore.maxLoadBrokers.replicationRate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.replicationRate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadBefore.replicationRate)} msg/sec</td>

                        <#assign maxClass = statusBefore.maxLoadBrokers.replicas?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.replicas?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${loadBefore.replicas}</td>

                        <#assign maxClass = statusBefore.maxLoadBrokers.leaders?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusBefore.minLoadBrokers.leaders?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${loadBefore.leaders}</td>
                    </tr>
                    <tr>
                        <th><span class="badge bg-neutral">After</span></th>

                        <#assign maxClass = statusAfter.maxLoadBrokers.size?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.size?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyDataSize(loadAfter.size)}</td>

                        <#assign maxClass = statusAfter.maxLoadBrokers.rate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.rate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadAfter.rate)} msg/sec</td>

                        <#assign maxClass = statusAfter.maxLoadBrokers.consumeRate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.consumeRate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadAfter.consumeRate)} msg/sec</td>

                        <#assign maxClass = statusAfter.maxLoadBrokers.replicationRate?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.replicationRate?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${util.prettyNumber(loadAfter.replicationRate)} msg/sec</td>

                        <#assign maxClass = statusAfter.maxLoadBrokers.replicas?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.replicas?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${loadAfter.replicas}</td>

                        <#assign maxClass = statusAfter.maxLoadBrokers.leaders?seq_contains(brokerId)?then(maxValueClass, "")>
                        <#assign minClass = statusAfter.minLoadBrokers.leaders?seq_contains(brokerId)?then(minValueClass, "")>
                        <#assign classes = (maxClass?has_content && minClass?has_content)?then("", minClass + " "+ maxClass)>
                        <td class="${classes}">${loadAfter.leaders}</td>
                    </tr>
                    <tr>
                        <th><span class="badge bg-primary">Diff</span></th>
                        <td class="small">
                            <#if loadAfter.size gt loadBefore.size>+</#if>${util.prettyDataSize(loadAfter.size - loadBefore.size)}
                        </td>
                        <td class="small">
                            <#if loadAfter.rate gt loadBefore.rate>+</#if>${util.prettyNumber(loadAfter.rate - loadBefore.rate)} msg/sec
                        </td>
                        <td class="small">
                            <#if loadAfter.consumeRate gt loadBefore.consumeRate>+</#if>${util.prettyNumber(loadAfter.consumeRate - loadBefore.consumeRate)} msg/sec
                        </td>
                        <td class="small">
                            <#if loadAfter.replicationRate gt loadBefore.replicationRate>+</#if>${util.prettyNumber(loadAfter.replicationRate - loadBefore.replicationRate)} msg/sec
                        </td>
                        <td class="small">
                            <#if loadAfter.replicas gt loadBefore.replicas>+</#if>${loadAfter.replicas - loadBefore.replicas}
                        </td>
                        <td class="small">
                            <#if loadAfter.leaders gt loadBefore.leaders>+</#if>${loadAfter.leaders - loadBefore.leaders}
                        </td>
                    </tr>
                </#list>
                <tr class="thead-light p-0">
                    <th colspan="100" class="p-1"></th>
                </tr>
                <tr>
                    <th>Average</th>
                    <th></th>
                    <td>${util.prettyDataSize(statusBefore.brokersAverageLoad.size)}</td>
                    <td>${util.prettyNumber(statusBefore.brokersAverageLoad.rate)} msg/sec</td>
                    <td>${util.prettyNumber(statusBefore.brokersAverageLoad.consumeRate)} msg/sec</td>
                    <td>${util.prettyNumber(statusBefore.brokersAverageLoad.replicationRate)} msg/sec</td>
                    <td>${statusBefore.brokersAverageLoad.replicas}</td>
                    <td>${statusBefore.brokersAverageLoad.leaders}</td>
                </tr>
                <tr>
                    <th rowspan="3">Disbalance<br/><small>|max - min|</small></th>
                    <th><span class="badge bg-secondary">Before</span></th>
                    <td>${util.prettyDataSize(statusBefore.brokersLoadDiff.size)}</td>
                    <td>${util.prettyNumber(statusBefore.brokersLoadDiff.rate)} msg/sec</td>
                    <td>${util.prettyNumber(statusBefore.brokersLoadDiff.consumeRate)} msg/sec</td>
                    <td>${util.prettyNumber(statusBefore.brokersLoadDiff.replicationRate)} msg/sec</td>
                    <td>${statusBefore.brokersLoadDiff.replicas}</td>
                    <td>${statusBefore.brokersLoadDiff.leaders}</td>
                </tr>
                <tr>
                    <th><span class="badge bg-neutral">After</span></th>
                    <td>${util.prettyDataSize(statusAfter.brokersLoadDiff.size)}</td>
                    <td>${util.prettyNumber(statusAfter.brokersLoadDiff.rate)} msg/sec</td>
                    <td>${util.prettyNumber(statusAfter.brokersLoadDiff.consumeRate)} msg/sec</td>
                    <td>${util.prettyNumber(statusAfter.brokersLoadDiff.replicationRate)} msg/sec</td>
                    <td>${statusAfter.brokersLoadDiff.replicas}</td>
                    <td>${statusAfter.brokersLoadDiff.leaders}</td>
                </tr>
                <tr>
                    <th><span class="badge bg-primary">Diff</span></th>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.size - statusBefore.brokersLoadDiff.size>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyDataSize(-change)}</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyDataSize(change)}</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.rate - statusBefore.brokersLoadDiff.rate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)} msg/sec</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)} mgs/sec</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.consumeRate - statusBefore.brokersLoadDiff.consumeRate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)} msg/sec</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)} mgs/sec</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.replicationRate - statusBefore.brokersLoadDiff.replicationRate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)} msg/sec</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)} mgs/sec</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.replicas - statusBefore.brokersLoadDiff.replicas>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">${change}</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${change}</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.brokersLoadDiff.leaders - statusBefore.brokersLoadDiff.leaders>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">${change}</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${change}</small>
                        </#if>
                    </td>
                </tr>
                <tr>
                    <th rowspan="3">Disbalance<br/>portion<br/><small>(100 * diff / avg)</small></th>
                    <th><span class="badge bg-secondary">Before</span></th>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.size)}%</td>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.rate)}%</td>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.consumeRate)}%</td>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.replicationRate)}%</td>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.replicas)}%</td>
                    <td>${util.prettyNumber(statusBefore.loadDiffPortion.leaders)}%</td>
                </tr>
                <tr>
                    <th><span class="badge bg-neutral">After</span></th>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.size)}%</td>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.rate)}%</td>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.consumeRate)}%</td>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.replicationRate)}%</td>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.replicas)}%</td>
                    <td>${util.prettyNumber(statusAfter.loadDiffPortion.leaders)}%</td>
                </tr>
                <tr>
                    <th><span class="badge bg-primary">Diff</span></th>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.size - statusBefore.loadDiffPortion.size>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.rate - statusBefore.loadDiffPortion.rate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.consumeRate - statusBefore.loadDiffPortion.consumeRate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.replicationRate - statusBefore.loadDiffPortion.replicationRate>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.replicas - statusBefore.loadDiffPortion.replicas>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                    <td>
                        <#assign change = statusAfter.loadDiffPortion.leaders - statusBefore.loadDiffPortion.leaders>
                        <#if change lte 0>
                            <small class="text-success font-weight-bold">-${util.prettyNumber(-change)}%</small>
                        <#else>
                            <small class="text-danger font-weight-bold">+${util.prettyNumber(change)}%</small>
                        </#if>
                    </td>
                </tr>
            </table>
        </div>
    </div>
    <br/>

    <div class="card">
        <div class="card-header">
            <h4>Data migration summary</h4>
        </div>
        <div class="card-body p-0">
            <table class="table table-hover m-0">
                <#assign dataMigration = proposedMigrations.dataMigration>
                <#include "../management/assignmentDataMigration.ftl">
            </table>
        </div>
    </div>
    <br/>

    <#assign maxBrokerIOBytes = proposedMigrations.dataMigration.maxBrokerIOBytes>
    <#include "../management/replicationThrottle.ftl">
    <br/>

    <div class="data" style="display: none;">
        <#list proposedMigrations.topicsAssignmentChanges as topicName, assignmentsChange>
            <#assign assignments = assignmentsChange.newAssignments>
            <#include "../management/assignmentData.ftl">
        </#list>
    </div>

</#if>