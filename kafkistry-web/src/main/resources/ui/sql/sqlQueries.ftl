
<#function sqlUrl sql>
    <#return "sql?query=${sql?markup_string?replace('        ', '')?url}">
</#function>

<#macro button sql text>
    <#assign url = sqlUrl(sql)>
    <a class="btn btn-sm btn-outline-dark" href="${url}">
        <span class="badge badge-primary">SQL</span> ${text}
    </a>
</#macro>

<#macro topClusterTopicReplicas cluster>
    <#assign sql>
        /* Top sized topics on cluster */
        SELECT t.Topic_topic as topic, sum(t.sizeBytes) as diskUsageBytes
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster == "${cluster}" AND t.sizeBytes > 0
        GROUP BY topic
        ORDER BY sum(t.sizeBytes) DESC
        LIMIT 20
    </#assign>
    <@button sql=sql text = "Top topic replicas sizes"/>
</#macro>

<#macro topClusterTopicPossibleUsageReplicas cluster>
    <#assign sql>
        /* Top possible disk usage topics on cluster */
        SELECT t.Topic_topic as topic, sum(c.value) as possibleUsedBytes, sum(t.sizeBytes) as actualSizeBytes
        FROM Topics_Replicas AS t
        JOIN Topics_ActualConfigs as c
        ON t.Topic_topic = c.Topic_topic AND t.Topic_cluster = c.Topic_cluster
        WHERE t.Topic_cluster == "${cluster}" AND c.key = "retention.bytes" AND c.value != -1
        GROUP BY topic
        ORDER BY possibleUsedBytes DESC
        LIMIT 20
    </#assign>
    <@button sql=sql text = "Top topic possible replicas sizes"/>
</#macro>

<#macro topClusterTopicUnboundedUsageReplicas cluster>
    <#assign sql>
        /* Top unbounded disk usage topics on cluster */
        SELECT t.Topic_topic as topic, sum(t.sizeBytes) as unboundedUsedBytes
        FROM Topics_Replicas AS t
        JOIN Topics_ActualConfigs as c
        ON t.Topic_topic = c.Topic_topic AND t.Topic_cluster = c.Topic_cluster
        WHERE t.Topic_cluster == "${cluster}" AND c.key = "retention.bytes" AND c.value == -1
        GROUP BY topic
        ORDER BY unboundedUsedBytes DESC
        LIMIT 20
    </#assign>
    <@button sql=sql text = "Top topic unbounded replicas sizes"/>
</#macro>

<#macro diskUsagePerBroker cluster>
    <#assign sql>
        /* Disk usage per broker on cluster */
        SELECT
            t.brokerId,
            count(*) numReplicas,
            count(DISTINCT t.Topic_topic) as numTopics,
            sum(t.sizeBytes) as totalSizeBytes
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster == "${cluster}"
        GROUP BY t.brokerId
        ORDER BY t.brokerId
        LIMIT 200
    </#assign>
    <@button sql=sql text = "Disk usage per broker"/>
</#macro>

<#macro orphanReplicasOnCluster cluster>
    <#assign sql>
        /* Orphan replicas on cluster */
        SELECT *
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster == "${cluster}" AND t.orphan
        LIMIT 200
    </#assign>
    <@button sql=sql text = "Orphan replicas list"/>
</#macro>

<#macro topClusterTopicReplicasPerBroker cluster numBrokers>
    <#assign sql>
        /* Top sized topics on cluster per broker */
        SELECT t.brokerId, t.Topic_topic as "topic", sum(t.sizeBytes) as "diskUsageBytes"
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster == "${cluster}" AND t.sizeBytes > 0
        GROUP BY t.brokerId, t.Topic_topic
        ORDER BY sum(t.sizeBytes) DESC
        LIMIT ${numBrokers * 10}
    </#assign>
    <@button sql=sql text = "Top topic replicas sizes per broker"/>
</#macro>

<#macro topClusterProducingTopics cluster>
    <#assign sql>
        /* Top produced topics on cluster */
        SELECT t.Topic_topic as topic, sum(t.producerRate) as produceRate
        FROM Topics_Partitions AS t
        WHERE t.Topic_cluster = "${cluster}"
        GROUP BY topic
        ORDER BY produceRate DESC
        LIMIT 20
    </#assign>
    <@button sql=sql text = "Top producing topics"/>
</#macro>

<#macro topClusterProducingTopicsPerBroker cluster numBrokers>
    <#assign sql>
        /* Top producing topics on cluster per broker*/
        SELECT r.brokerId, t.Topic_topic as topic, sum(t.producerRate) as produceRate
        FROM Topics_Partitions AS t
        JOIN Topics_Replicas AS r
        ON t.Topic_cluster = r.Topic_cluster AND topic = r.Topic_topic AND t.partition = r.partition
        WHERE t.Topic_cluster = "${cluster}"
        GROUP BY r.brokerId, topic
        ORDER BY produceRate DESC
        LIMIT ${numBrokers * 10}
    </#assign>
    <@button sql=sql text = "Top producing topics per broker"/>
</#macro>

<#macro topicProduceRates cluster topic>
    <#assign sql>
        /* Topic partitions produce rates */
        SELECT t.partition, t.producerRate
        FROM Topics_Partitions AS t
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        LIMIT 1000
    </#assign>
    <@button sql=sql text = "Partition rates"/>
</#macro>

<#macro topicSizeVsTimeRetention cluster topic>
    <#assign sql>
        /* Topic partitions time vs. size retention usage */
        SELECT
        t.partition as partition,
        100.0 * t.oldestRecordAgeMs / crt.value as "Time Retention %",
        100.0 * r.sizeBytes / crs.value as "Size Retention %"
        FROM Topics_Partitions AS t
        JOIN Topics_ActualConfigs as crt ON t.Topic_cluster == crt.Topic_cluster AND t.Topic_topic == crt.Topic_topic AND crt.key == "retention.ms"
        JOIN Topics_ActualConfigs as crs ON t.Topic_cluster == crs.Topic_cluster AND t.Topic_topic == crs.Topic_topic AND crs.key == "retention.bytes"
        JOIN Topics_Replicas as r ON t.Topic_cluster == r.Topic_cluster AND t.Topic_topic == r.Topic_topic AND t.partition == r.partition AND r.leader
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        LIMIT 1000
    </#assign>
    <@button sql=sql text = "Time vs size retention"/>
</#macro>

<#macro topicReplicaSizes cluster topic>
    <#assign sql>
        /* Topic replicas sizes per partition per broker */
        SELECT t.partition, t.brokerId, t.sizeBytes
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        LIMIT 1000
    </#assign>
    <@button sql=sql text = "Replica sizes"/>
</#macro>

<#macro topicBrokerReplicaSizes cluster topic>
    <#assign sql>
        /* Topic replicas total size per broker */
        SELECT t.brokerId, sum(t.sizeBytes) as brokerDiskUsageBytes
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        GROUP BY t.brokerId
        LIMIT 1000
    </#assign>
    <@button sql=sql text = "Replica sizes per broker"/>
</#macro>

<#macro topicReplicaLeadersCounts cluster topic>
    <#assign sql>
        /* Topic replicas/leaders count per broker */
        SELECT
          t.brokerId,
          count(*) as replicasPerBroker,
          sum(case when t.leader then 1 else 0 end) as leadersPerBroker
        FROM Topics_Replicas AS t
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        GROUP BY t.brokerId
        LIMIT 1000
    </#assign>
    <@button sql=sql text = "Replicas/leaders per broker"/>
</#macro>

<#macro topicReassignmeentProgress cluster topic>
    <#assign sql>
        /* Topic re-assignment progress per partition */
        SELECT
        t.partition,
        100.0 * min(t.sizeBytes) / l.sizeBytes as "replication %"
        FROM Topics_Replicas AS t
        JOIN Topics_Replicas as l
        ON l.leader AND l.Topic_cluster = t.Topic_cluster AND l.Topic_topic = t.Topic_topic AND l.partition = t.partition
        WHERE t.Topic_cluster = "${cluster}" AND t.Topic_topic = "${topic}"
        GROUP BY t.partition
        LIMIT 1000

    </#assign>
    <@button sql=sql text = "Re-assignmeent progress"/>
</#macro>




