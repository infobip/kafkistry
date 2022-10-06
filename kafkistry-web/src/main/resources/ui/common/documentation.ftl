<#assign editPendingChangeBtn = "Open topic form with data populated from latest state in this branch.
<br/><strong>NOTE</strong>: Write operation after you make a save action, will be written to some branch depending on cofiguration of this service.
(it might or might not be the same branch)">
<#assign refreshGitBtn = "Do fetch all new changes from remiote git to local clone right now. Use it when some change,
such as merge, is performed on remote git and you do not want to wait next automatic refresh for Kafkistry to 'see' new changes">

<#assign createTopicOnKafkaBtn = "Open view to create missing topic on spoecific kafka cluster">
<#assign suggestedEditBtn = "Open topic edit form with populated data in a way that suggested configuration matches current
existing configuration on all clusters">
<#assign manualEditBtn = "Open topic edit form with data populated using current topic configuration in registry on master">
<#assign fixViolationsEditBtn = "Open topic edit form with data populated in such way that current topic configuration is a
ltered minimaly to avoid rule violations">
<#assign importTopicBtn = "Open topic import form with data populated in such way that given configuration matches
configuration for this topic on all clusters">
<#assign alterTopicConfigBtn = "Open view page to initiale altering of an on-cluster-topic's configuration to match
configuration defined by registry">
<#assign alterTopicPartitionCountBtn = "Open view page to show options to resolve topic's partition count issue. Offered
action is to add topic partitions on cluster to match partition count in registry.">
<#assign alterTopicReplicationFactorBtn = "Open view page to show options to resolve topic's replication factor issue.
Offered action is to add new partition replicas on cluster to match replication factor in registry.">
<#assign deleteClusterTopicBtn = "Open view page to initiate deletion of topic fromm kafka cluster.">
<#assign reBalanceTopicReplicaAssignments = "Open view page to show proposed re-assignment to resolve topic's disbalance.
Offered action is to move some partition replicas and/or re-elect different partition leaders.">
<#assign applyReBalanceAssignmentsBtn = "This will trigger partition replica re-assignments
with generated re-balance assignments as shown. It will use specified throttling to prevent cluster overload.">
<#assign verifyReAssignmentsBtn = "This will check status of re-assignments of topic's partition replicas.
In case when re-assignment is successfuly completed, throttle configuration from topic will be removed and in case
there are no in-progress re-assignments on cluster, throttle rate will be removed from brokers too.">
<#assign preferedReplicaLeaderElectionBtn = "Perform election of preferred leader replica. This operation may be needed to
perform after preferred leader re-balance.">
<#assign showTopicInspectionBtn = "Open topic inspection page to see actual configuration and assignments table.">

<#assign updateInputMsg = "This can be used descriptive update reason which will be inserted into commit message">

<#assign allClustersPresenceOption = "Expected to exist on 'all' clusters listed in registry, when the new cluster is added, it's also expected that it should exist on it.">
<#assign onlyOnClustersPresenceOption = "Expected to exist 'only on' selected clusters, when the new cluster is added, it won't be expected to exist on it.">
<#assign notOnClustersPresenceOption = "Expected to exist on all except selected clusters, when the new cluster is added, it's also expected that it should exist on it.">
<#assign taggedClustersPresenceOption = "Expected to exist on all clusters which have specified tag, when the tag is added to cluster, it's also expected that it should exist on it.">

<#assign enablePropertiesOverrides="Enable option to provide different properties (partition-count, replication-factor) override for cluster. If unchecked, then use global partition properties.">
<#assign dryRunTopicInspectBtn="Show what would be the result of topic inspection if the topic would be saved with given configuration">

<#assign wizardGoToCreateTopic = "This will open the standard Create topic form with prefilled data from this wizard. Then you can change something if you want and create the topic.">
<#assign wizardNeedHighAvailabilityHelp = "<ul>
    <li>Checked means replication of each partition to different kafka brokers</li>
    <li>Unchecked means no replication.</li>
    <li>No replication means that when one broker is down producing and consuming won't be possible to partitions which are assigned to this broker.</li>
    <li>Data will be lost only if storage fails that it can't be recovered.</li>
</ul>">
<#assign wizardTolerateDataLossHelp = "<ul>
    <li>This makes effect only when HA is enabled.</li>
    <li>If unchecked producers will be allowed (not suspended) to produce only if none or at-most one broker is not in-sync with others.</li>
    <li>If checked producers will be allowed to produce if just one (leader) broker is up.</li>
    <li>In other words, not tolerating data loss means that producer writes need to be written in almost all replicas, otherwise producing of new traffic will be suspended.</li>
</ul>">

<#assign allOk = "Everything is ok with topic on all clusters">
<#assign configOk = "Topic has configuration issue on some cluster(s)">
<#assign visibleOk = "Some clusters are not reachable">
<#assign ruleCheckOk = "Topic has some rule check violated on some cluster(s)">
<#assign runtimeOk = "Topic has some runtime issues on kafka on some cluster(s)">

<#assign filterHelp>
    <table class='table table-sm mb-0'>
        <tr>
            <td>Json field</td>
            <td>Works only for json formatted records</td>
        </tr>
        <tr>
            <td>Record header</td>
            <td>Headers are treated as Map of <code>key</code> &rarr; <code>value</code></td>
        </tr>
        <tr>
            <td>Key name syntax</td>
            <td>
                <span>Selector examples:</span>
                <ul class='mb-0'>
                    <li><code>myKey</code> field in object</li>
                    <li><code>myKey.subKey</code> field in object within an object</li>
                    <li><code>*.subKey</code> specific field within any root field</li>
                    <li><code>myList[3]</code> 4-th element in a list of root field</li>
                    <li><code>myList[*]</code> any element in a list</li>
                    <li><code>com\.foo\.Bar</code> escaping dots in single key name</li>
                </ul>
            </td>
        </tr>
    </table>
</#assign>

<#assign aclHostHelpMsg = "IP address(es) from which host(s) is principal (kafka client) allowed to connect so that this rule applies">
<#assign dryRunTopicInspectAclsBtn="Show what would be the result of principal ACLs inspection if the rules would be saved with given setup">
