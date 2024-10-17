<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->

<#-- @ftlvariable name="clusterIdentifier" type="java.lang.String" -->
<#-- @ftlvariable name="topicName" type="java.lang.String" -->
<#-- @ftlvariable name="clusterInfo"  type="com.infobip.kafkistry.kafka.ClusterInfo" -->
<#-- @ftlvariable name="partitionsAssignments"  type="java.util.List<com.infobip.kafkistry.kafka.PartitionAssignments>" -->
<#-- @ftlvariable name="assignmentsDisbalance"  type="com.infobip.kafkistry.service.generator.AssignmentsDisbalance" -->
<#-- @ftlvariable name="partitionChange"  type="com.infobip.kafkistry.service.topic.PartitionPropertyChange" -->

<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="topicReplicas" type="com.infobip.kafkistry.kafkastate.TopicReplicaInfos" -->
<#-- @ftlvariable name="partitionReAssignments" type="java.util.Map<Partition, com.infobip.kafkistry.kafka.TopicPartitionReAssignment>" -->

<#function getReplicaAssignment partitionAssignments brokerId>
<#-- @ftlvariable name="partitionAssignments"  type="com.infobip.kafkistry.kafka.PartitionAssignments" -->
<#-- @ftlvariable name="brokerId"  type="java.lang.Integer" -->
    <#list partitionAssignments.replicasAssignments as replica>
        <#if replica.brokerId == brokerId>
            <#return replica>
        </#if>
    </#list>
</#function>

<#import "../common/util.ftl" as _util>
<#import "../common/infoIcon.ftl" as _info_>
<#import "../sql/sqlQueries.ftl" as sql>

<#assign tinyMode = clusterInfo.brokerIds?size gt 6>

<div class="card mb-2">
    <div class="card-header">
        <span class="h4">Partition replicas assignments over brokers</span>
        <span class="ml-3">
            <@sql.topicReplicaSizes cluster=clusterInfo.identifier topic=topicName/>
            <@sql.topicBrokerReplicaSizes cluster=clusterInfo.identifier topic=topicName/>
            <@sql.topicReplicaLeadersCounts cluster=clusterInfo.identifier topic=topicName/>
        </span>
    </div>
    <div class="card-body p-0">

        <#if clusterInfo??>
            <#if assignmentsDisbalance??>
                <div class="p-2">
                    <#if assignmentsDisbalance.replicasDisbalance == 0 && assignmentsDisbalance.leadersDisbalance == 0
                        && assignmentsDisbalance.partitionsPerRackDisbalance.totalDisbalance == 0>
                        <div class="alert alert-success">Partition and leader assignments over brokers is optimal</div>
                    <#else>
                        <div class="row m-0">
                            <div class="col p-1">
                                <#if assignmentsDisbalance.replicasDisbalance gt 0>
                                    <div class="alert alert-warning">Partition assignments disbalance
                                        is ${assignmentsDisbalance.replicasDisbalance} (# of needed replica migrations)
                                        | ${assignmentsDisbalance.replicasDisbalancePercent?string["0.##"]}% of all replicas
                                    </div>
                                <#else>
                                    <div class="alert alert-success">Partition replicas placement over brokers is optimal</div>
                                </#if>
                            </div>
                            <div class="col p-1">
                                <#if assignmentsDisbalance.leadersDisbalance gt 0>
                                    <div class="alert alert-warning">Partition leaders disbalance
                                        is ${assignmentsDisbalance.leadersDisbalance} (# of needed re-orders with elections)
                                        | ${assignmentsDisbalance.leadersDisbalancePercent?string["0.##"]}% of all partitions
                                    </div>
                                <#else>
                                    <div class="alert alert-success">
                                        Partition leaders distribution over brokers is optimal
                                    </div>
                                </#if>
                            </div>
                            <div class="col p-1">
                                <#if assignmentsDisbalance.partitionsPerRackDisbalance.totalDisbalance gt 0>
                                    <div class="alert alert-warning">
                                        <#assign singleRckPart = assignmentsDisbalance.partitionsPerRackDisbalance.singleRackPartitions>
                                        <#if singleRckPart?size gt 0>
                                            Partitions [<#list singleRckPart as p><code>${p}</code><#if p?has_next>, </#if></#list>]
                                            have replicas on brokers of the same rack.
                                        </#if>
                                        <#assign disbalancedRckPart = []>
                                        <#list assignmentsDisbalance.partitionsPerRackDisbalance.partitionDisbalance as p, d>
                                            <#if d gt 0 && !singleRckPart?seq_contains(p)>
                                                <#assign disbalancedRckPart += [p]>
                                            </#if>
                                        </#list>
                                        <#if disbalancedRckPart?size gt 0>
                                            Partitions [<#list disbalancedRckPart as p><code>${p}</code><#if p?has_next>, </#if></#list>]
                                            have un-even distribution across brokers on different racks.
                                        </#if>
                                    </div>
                                </#if>
                            </div>
                        </div>
                        <a href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterInfo.identifier, "REPLICAS_THEN_LEADERS")}">
                            <button class="btn btn-info btn-sm mb-1">Suggest re-balance...</button>
                        </a>
                    </#if>
                </div>
            </#if>

            <#import "../clusters/clusterNodesList.ftl" as brokerBadge>

            <div style="overflow-x: auto; max-height: 90vh;">
                <table id="assignments" class="partitions-assignments table table-sm sticky-table-headers m-0">
                    <thead class="sticky-header">
                    <tr class="thead-dark">
                        <th class="sticky-header text-center">
                            <div class="text-right">
                                Brokers
                            </div>
                            <div style="height: 6px;" class="align-content-center">
                                <div style="height: 1px; background-color: white; transform: rotate(10deg)"></div>
                            </div>
                            <div class="text-left">
                                Partition
                            </div>
                        </th>
                        <#if assignmentStatus.clusterHasRacks><th></th></#if>
                        <#list (clusterInfo.brokerIds) as brokerId>
                            <th class="text-center" style="width: ${100/(1+clusterInfo.nodeIds?size)}%">
                                <@brokerBadge.clusterNodeId nodeId=brokerId/>
                            </th>
                        </#list>
                    </tr>
                    <#if topicReplicas??>
                        <tr class="thead-light">
                            <th class="sticky-header text-center">
                                <span title="All size sum">
                                    ${_util.prettyDataSize(topicReplicas.totalSizeBytes)}
                                </span>
                            </th>
                            <#if assignmentStatus.clusterHasRacks>
                                <th>Racks</th>
                            </#if>
                            <#list clusterInfo.brokerIds as brokerId>
                                <th class="text-center <#if tinyMode>small font-weight-bold</#if>">
                                    <#assign brokerTotalBytes = (topicReplicas.brokerTotalSizes?api.get(brokerId))!0>
                                    ${_util.prettyDataSize(brokerTotalBytes)}
                                </th>
                            </#list>
                        </tr>
                    </#if>
                    </thead>
                    <#list assignmentStatus.partitions as partition>
                        <tbody class="<#if tinyMode>small</#if>">
                        <tr>
                            <#assign rackUsageDisbalanced = assignmentsDisbalance?? && assignmentsDisbalance.partitionsPerRackDisbalance.partitionDisbalance?api.get(partition.partition) gt 0>
                            <#assign singleRackWarning = assignmentStatus.clusterHasRacks && partition.singleRackReplicas>
                            <td class="sticky-header font-weight-bold text-center align-middle <#if singleRackWarning>alert-danger<#elseif rackUsageDisbalanced>alert-warning</#if>">
                                ${partition.partition}
                            </td>
                            <#if assignmentStatus.clusterHasRacks>
                                <td class="small <#if singleRackWarning>alert-danger<#elseif rackUsageDisbalanced>alert-warning</#if>">
                                    <#if singleRackWarning>
                                        <span class="badge badge-warning" title="This partition is hosted by brokers of same rack">Single rack</span>
                                    </#if>
                                    <#list partition.rackCounts as rackStatus>
                                        <#assign rackTitle = (rackStatus.oldCount == rackStatus.newCount)?then(
                                            "Rack=${rackStatus.rack}, #replicas=${rackStatus.newCount}",
                                            "Rack=${rackStatus.rack}, #replicas-before=${rackStatus.oldCount}, #replicas-after=${rackStatus.newCount}"
                                        )>
                                        <div class="text-nowrap <#if rackStatus.newCount == 0>crossed</#if>"
                                             title="${rackTitle}<#if rackStatus.newCount == 0>, to remove</#if>">
                                            <#if rackStatus.oldCount == rackStatus.newCount>
                                                <span class="badge badge-secondary">${rackStatus.newCount}</span>
                                            <#else>
                                                <#if rackStatus.newCount gt rackStatus.oldCount>
                                                    <#if rackStatus.oldCount == 0>
                                                        <span class="badge badge-success">+${rackStatus.newCount}</span>
                                                    <#else>
                                                        <span class="btn-group">
                                                            <span class="badge badge-secondary" style="border-top-right-radius: 0; border-bottom-right-radius: 0;">
                                                                ${rackStatus.oldCount}
                                                            </span>
                                                            <span class="badge badge-success" style="border-top-left-radius: 0; border-bottom-left-radius: 0;">
                                                                +${rackStatus.newCount-rackStatus.oldCount}
                                                            </span>
                                                        </span>
                                                    </#if>
                                                </#if>
                                                <#if rackStatus.oldCount gt rackStatus.newCount>
                                                    <#if rackStatus.newCount == 0>
                                                        <span class="badge badge-danger">-${rackStatus.oldCount}</span>
                                                    <#else>
                                                        <span class="btn-group">
                                                            <span class="badge badge-secondary" style="border-top-right-radius: 0; border-bottom-right-radius: 0;">
                                                                ${rackStatus.oldCount}
                                                            </span>
                                                            <span class="badge badge-danger" style="border-top-left-radius: 0; border-bottom-left-radius: 0;">
                                                                -${rackStatus.oldCount-rackStatus.newCount}
                                                            </span>
                                                        </span>
                                                    </#if>
                                                </#if>
                                            </#if>
                                            <code>${rackStatus.rack}</code>
                                        </div>
                                    </#list>
                                </td>
                            </#if>

                            <#assign hasReAssignment = (partitionReAssignments?api.get(partition.partition))??>
                            <#if hasReAssignment>
                                <#assign reAssignment = partitionReAssignments?api.get(partition.partition)>
                            </#if>
                            <#assign brokerReplicas = partition.brokerReplicas>
                            <#list brokerReplicas as brokerReplica>
                                <td class="p-1">
                                    <#assign reAssignAdding = hasReAssignment && reAssignment.addingReplicas?seq_contains(brokerReplica.brokerId)>
                                    <#assign reAssignRemoving = hasReAssignment && reAssignment.removingReplicas?seq_contains(brokerReplica.brokerId)>
                                    <#assign data = {"text": "", "alert": ""}>
                                    <#if brokerReplica.currentStatus??>
                                        <#assign replica = brokerReplica.currentStatus>
                                        <#if replica.leader>
                                            <#assign data = {"text": "Leader", "alert": "alert-primary", "explain": "This replica is currently the leader"}>
                                        <#elseif replica.inSyncReplica>
                                            <#assign data = {"text": "In-sync", "alert": "alert-secondary", "explain": "This replica is currently in-sync with the leader of partition"}>
                                        <#elseif reAssignAdding>
                                            <#assign data = {"text": "Out-of-sync", "alert": "alert-warning", "explain": "This replica is being added by re-assignment and currently out-of-sync"}>
                                        <#else>
                                            <#assign data = {"text": "Out-of-sync", "alert": "alert-danger", "explain": "This replica is currently not in sync wioth the leader of the partition"}>
                                        </#if>
                                    <#elseif brokerReplica.added>
                                        <#assign data = {"text": "New", "alert": "alert-success", "explain": "This replica will be added"}>
                                    <#elseif brokerReplica.removed>
                                        <#assign data = {"text": "Remove", "alert": "alert-warning", "explain": "This replica will be removed"}>
                                    <#else>
                                        <#assign data = {"text": "", "alert": ""}>
                                    </#if>
                                    <#assign leaderData = {"text": "", "class": ""}>
                                    <#if brokerReplica.newLeader>
                                        <#assign leaderData = {"text": "+L", "class": "text-success", "explain": "This replica will become preferred leader"}>
                                    <#elseif brokerReplica.exLeader>
                                        <#assign leaderData = {"text": "-L", "class": "text-danger", "explain": "This replica will stop being preferred leader"}>
                                    <#elseif (brokerReplica.currentStatus.preferredLeader && !brokerReplica.currentStatus.leader)!false>
                                        <#assign leaderData = {"text": "*L", "class": "text-primary", "explain": "This replica is preferred leader but it is not currently a leader"}>
                                    <#elseif (!brokerReplica.currentStatus.preferredLeader && brokerReplica.currentStatus.leader)!false>
                                        <#assign leaderData = {"text": "L*", "class": "text-secondary", "explain": "This replica is not preferred leader but it is currently a leader"}>
                                    </#if>

                                    <#assign crossed = brokerReplica.removed || reAssignRemoving>
                                    <div class="alert m-0 p-1 text-nowrap ${data['alert']} <#if crossed>crossed</#if>">
                                        <#assign haveReplicaInfo = false>
                                        <#-- @ftlvariable name="replicaInfo" type="com.infobip.kafkistry.kafka.TopicPartitionReplica" -->
                                        <#if topicReplicas??>
                                            <#assign b = brokerReplica.brokerId>
                                            <#assign p = partition.partition>
                                            <#if (topicReplicas.brokerPartitionReplicas?api.get(b)?api.get(p))??>
                                                <#assign replicaInfo = topicReplicas.brokerPartitionReplicas?api.get(b)?api.get(p)>
                                                <#assign haveReplicaInfo = true>
                                            </#if>
                                        </#if>
                                        <#if data["text"] != "">
                                            <#assign tooltip>
                                                <ul>
                                                    <#if (data["explain"])??>
                                                        <li>${data["explain"]}</li></#if>
                                                    <#if (leaderData["explain"])??>
                                                        <li>${leaderData["explain"]}</li></#if>
                                                    <#if brokerReplica.removed>
                                                        <li>This replica wil be removed</li></#if>
                                                    <#if 0 <= brokerReplica.rank>
                                                        <li>
                                                            <#if brokerReplica.rank == 0>1st
                                                            <#elseif brokerReplica.rank == 1>2nd
                                                            <#elseif brokerReplica.rank == 2>3rd
                                                            <#else>${brokerReplica.rank+1}th
                                                            </#if>
                                                            priority preferred replica
                                                        </li>
                                                    </#if>
                                                    <#if reAssignAdding>
                                                        <li>This replica is being <strong>added</strong> by re-assignment</li>
                                                    </#if>
                                                    <#if reAssignRemoving>
                                                        <li>This replica is being <strong>removed</strong> by re-assignment</li>
                                                    </#if>
                                                    <#if haveReplicaInfo>
                                                        <#if replicaInfo.future>
                                                            <li>This replica is 'future' assignment meaning it will
                                                                became part of replica set after re-assignment finish
                                                                sync
                                                            </li>
                                                        </#if>
                                                        <li>Size on disk:
                                                            <code>${_util.prettyDataSize(replicaInfo.sizeBytes)}</code>
                                                        </li>
                                                        <li>Root dir: <code>${replicaInfo.rootDir}</code></li>
                                                        <#if !(brokerReplica.currentStatus??) || brokerReplica.currentStatus.leader>
                                                            <li>
                                                                Lag diff between HW and LEO:
                                                                <#if replicaInfo.offsetLag gt 0>
                                                                    <span class='badge badge-danger'>Lag: ${_util.prettyNumber(replicaInfo.offsetLag)}</span>
                                                                <#else>
                                                                    <span class='badge badge-success'>No lag</span>
                                                                </#if>
                                                            </li>
                                                        </#if>
                                                    </#if>
                                                    <li>
                                                        Broker rack:
                                                        <#if brokerReplica.rack??>
                                                            <code>${brokerReplica.rack}</code>
                                                        <#else>
                                                            <i>(NULL)</i>
                                                        </#if>
                                                    </li>
                                                </ul>
                                            </#assign>
                                            <span class="p-1">
                                                <@_info_.icon tooltip=tooltip/>
                                            </span>
                                        </#if>
                                        <#if 0 <= brokerReplica.rank>
                                            <span class="small">[${brokerReplica.rank?c}]</span>
                                        </#if>
                                        <#if tinyMode>
                                            <br/>
                                        </#if>
                                        <span>${data["text"]}</span>
                                        <strong class="${leaderData['class']}">${leaderData["text"]}</strong>
                                        <#if haveReplicaInfo>
                                            <br/>
                                            <div class="small mt-1 text-center">
                                                <span title="Size of replica">${_util.prettyDataSize(replicaInfo.sizeBytes)}</span>
                                                <#if replicaInfo.future>
                                                    <br/>
                                                    <span class="badge badge-light">future replica</span>
                                                </#if>
                                            </div>
                                        </#if>
                                    </div>
                                </td>
                            </#list>
                        </tr>
                        </tbody>
                    </#list>
                </table>
            </div>

        <#else>
            <div class="alert alert-danger">
                Cluster is unreachable, can't show assignments
            </div>
        </#if>
    </div>
</div>

