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

<#assign tinyMode = clusterInfo.nodeIds?size gt 6>

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
                    <#if assignmentsDisbalance.replicasDisbalance == 0 && assignmentsDisbalance.leadersDisbalance == 0>
                        <div class="alert alert-success">Partition and leader assignments over brokers is optimal</div>
                    <#else>
                        <#if assignmentsDisbalance.replicasDisbalance gt 0>
                            <div class="alert alert-warning">Partition assignments disbalance
                                is ${assignmentsDisbalance.replicasDisbalance} (# of needed replica migrations)
                                | ${assignmentsDisbalance.replicasDisbalancePercent?string["0.##"]}% of all replicas
                            </div>
                        <#else>
                            <div class="alert alert-success">Partition replicas placement over brokers is optimal</div>
                        </#if>
                        <#if assignmentsDisbalance.leadersDisbalance gt 0>
                            <div class="alert alert-warning">Partition leaders disbalance
                                is ${assignmentsDisbalance.leadersDisbalance} (# of needed re-orders with elections)
                                | ${assignmentsDisbalance.leadersDisbalancePercent?string["0.##"]}% of all partitions
                            </div>
                        <#else>
                            <div class="alert alert-success">Partition leaders distribution over brokers is optimal
                            </div>
                        </#if>
                        <a href="${appUrl.topicsManagement().showTopicReBalance(topicName, clusterInfo.identifier, "REPLICAS_THEN_LEADERS")}">
                            <button class="btn btn-info btn-sm mb-1">Suggest re-balance...</button>
                        </a>
                    </#if>
                </div>
            </#if>

            <#import "../clusters/clusterNodesList.ftl" as brokerBadge>

            <div style="overflow-x: scroll;">
                <table id="assignments" class="partitions-assignments table table-sm m-0">
                    <thead>
                    <tr class="bg-light">
                        <th></th>
                        <th class="text-center" colspan="100">Brokers</th>
                    </tr>
                    <tr class="thead-dark">
                        <th class="text-center">Partition</th>
                        <#list clusterInfo.nodeIds as brokerId>
                            <th class="text-center" style="width: ${100/(1+clusterInfo.nodeIds?size)}%">
                                <@brokerBadge.clusterBrokerId brokerId=brokerId/>
                            </th>
                        </#list>
                    </tr>
                    <tr class="thead-light">
                        <th class="text-center">
                            ALL<br/>
                            ${_util.prettyDataSize(topicReplicas.totalSizeBytes)}
                        </th>
                        <#list clusterInfo.nodeIds as brokerId>
                            <th class="text-center">
                                <#assign brokerTotalBytes = (topicReplicas.brokerTotalSizes?api.get(brokerId))!0>
                                ${_util.prettyDataSize(brokerTotalBytes)}
                            </th>
                        </#list>
                    </tr>
                    </thead>
                    <#list assignmentStatus.partitions as partition>
                        <tbody class="<#if tinyMode>small</#if>">
                        <tr>
                            <td class="font-weight-bold text-center align-middle">${partition.partition}</td>

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

