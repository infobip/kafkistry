<#-- @ftlvariable name="assignmentStatus" type="com.infobip.kafkistry.service.topic.PartitionsAssignmentsStatus" -->
<#-- @ftlvariable name="dataMigration" type="com.infobip.kafkistry.service.topic.DataMigration" -->
<#import "../common/util.ftl" as __util>

<tr>
    <th>Total number of added partition replicas</th>
    <td>${assignmentStatus.newReplicasCount}</td>
</tr>
<tr>
    <th>Number of moved/migrated partition replicas</th>
    <td>${assignmentStatus.movedReplicasCount}</td>
</tr>
<tr>
    <th>Number of re-electing partition leaders</th>
    <td>${assignmentStatus.reElectedLeadersCount}</td>
</tr>

<#include "assignmentDataMigration.ftl">
