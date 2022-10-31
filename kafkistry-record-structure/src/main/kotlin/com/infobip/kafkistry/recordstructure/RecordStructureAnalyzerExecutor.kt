package com.infobip.kafkistry.recordstructure

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Summary
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.kafka.SamplingPosition
import com.infobip.kafkistry.kafka.RecordSampler
import com.infobip.kafkistry.kafka.RecordSamplingListener
import com.infobip.kafkistry.metric.MetricHolder
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val sampledMessagesCountHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_sampled_count
    Counter.build()
        .name(prefix + "record_analyzer_sampled_count")
        .help("Number of sampled messages for structure analysis")
        .labelNames("cluster", "topic")
        .register()
}

private val failedMessagesCountHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_failed_count
    Counter.build()
        .name(prefix + "record_analyzer_failed_count")
        .help("Number of failed sampling messages for structure analysis")
        .labelNames("cluster", "topic")
        .register()
}

private val droppedMessagesCountHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_dropped_count
    Counter.build()
        .name(prefix + "record_analyzer_dropped_count")
        .help("Number of dropped messages for structure analysis due to full queue")
        .register()
}

private val queueSizeHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_queue_size
    Gauge.build()
        .name(prefix + "record_analyzer_queue_size")
        .help("Number of records queued for processing")
        .register()
}

private val recordsStructuresCountHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_records_structures_count
    Counter.build()
        .name(prefix + "record_analyzer_records_structures_count")
        .help("Number of records structures processed by the procedure")
        .labelNames("records_structures_procedure")
        .register()
}

private val jobExecutionsCountHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_records_execution_count
    Counter.build()
        .name(prefix + "record_analyzer_records_execution_count")
        .help("Number of executed job by job state")
        .labelNames("job_execution_state")
        .register()
}

private val analyzeLatencyHolder = MetricHolder { prefix ->
    //default name: kafkistry_record_analyzer_latencies
    Summary.build()
        .name(prefix + "record_analyzer_latencies")
        .help("Summary of latencies of analysis of sampled records")
        .ageBuckets(5)
        .maxAgeSeconds(TimeUnit.MINUTES.toSeconds(5))
        .quantile(0.5, 0.05)   // Add 50th percentile (= median) with 5% tolerated error
        .quantile(0.9, 0.01)   // Add 90th percentile with 1% tolerated error
        .quantile(0.99, 0.001) // Add 99th percentile with 0.1% tolerated error
        .register()
}

@Component
@ConditionalOnProperty("app.record-analyzer.enabled", matchIfMissing = true)
class RecordStructureAnalyzerExecutor(
    private val analyzer: RecordStructureAnalyzer,
    private val issuesRegistry: BackgroundJobIssuesRegistry,
    private val properties: RecordAnalyzerProperties,
    private val analyzeFilter: AnalyzeFilter,
    promProperties: PrometheusMetricsProperties,
) : RecordSamplingListener, SmartLifecycle {

    private val executor = Executors.newFixedThreadPool(
        properties.executor.concurrency, CustomizableThreadFactory("record-analyzer-")
    )

    /**
     * Accept all sampled records into this queue and process each by different processing thread(s)
     */
    private val queue: Queue<Pair<ClusterRef, ConsumerRecord<ByteArray?, ByteArray?>>> =
        ArrayDeque(properties.executor.maxQueueSize)
    private val queueLock = ReentrantLock()
    private val queueCondition = queueLock.newCondition()

    private val sampledMessagesCount = sampledMessagesCountHolder.metric(promProperties)
    private val failedMessagesCount = failedMessagesCountHolder.metric(promProperties)
    private val droppedMessagesCount = droppedMessagesCountHolder.metric(promProperties)
    private val queueSize = queueSizeHolder.metric(promProperties)
    private val recordsStructuresCount = recordsStructuresCountHolder.metric(promProperties)
    private val jobExecutionsCount = jobExecutionsCountHolder.metric(promProperties)
    private val analyzeLatency = analyzeLatencyHolder.metric(promProperties)

    private fun incrementTrimRecordsStructuresCount(counter: Int) {
        recordsStructuresCount.labels("trim").inc(counter.toDouble())
    }

    private fun incrementDumpRecordsStructuresCount(counter: Int) {
        recordsStructuresCount.labels("dump").inc(counter.toDouble())
    }

    @Volatile
    private var running = false

    override fun isAutoStartup(): Boolean = true

    override fun start() {
        running = true
        repeat(properties.executor.concurrency) {
            executor.submit { processWhileRunning() }
        }
    }

    override fun stop() {
        running = false
        queueLock.withLock { queueCondition.signalAll() }
    }

    override fun isRunning(): Boolean = running

    override fun need(
        samplingPosition: SamplingPosition, clusterRef: ClusterRef, topicName: TopicName
    ): Boolean {
        return samplingPosition == SamplingPosition.NEWEST && analyzeFilter.shouldAnalyze(clusterRef, topicName)
    }

    override fun sampler(samplingPosition: SamplingPosition, clusterRef: ClusterRef) =
        object : RecordSampler {

            /**
             * Accepts one sampled message and puts it into queue to be processed by another thread
             * to be able to return ASAP to avoid choking upstream caller.
             */
            override fun acceptRecord(consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>) {
                queueLock.withLock {
                    val reachedMaxCapacity = queue.size >= properties.executor.maxQueueSize
                    if (reachedMaxCapacity) {
                        queue.poll()    //drop record, better to skip some than exhaust memory
                        droppedMessagesCount.inc()
                    }
                    queue.offer(clusterRef to consumerRecord)
                    queueCondition.signal()
                }
                updateQueueSizeMetric()
            }

        }

    override fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) {
        analyzer.removeCluster(clusterIdentifier)
    }

    private fun updateQueueSizeMetric() = queueSize.set(queue.size.toDouble())

    private fun processWhileRunning() {
        while (running) {
            queueLock.withLock { queue.poll() }
                ?.let { (cluster, record) ->
                    processRecord(cluster, record)
                }
                .also { updateQueueSizeMetric() }
                ?: queueLock.withLock {
                    queueCondition.await(500, TimeUnit.MILLISECONDS) //nothing in queue, try pool later
                }
        }
    }

    private fun processRecord(
        cluster: ClusterRef, record: ConsumerRecord<ByteArray?, ByteArray?>
    ) {
        sampledMessagesCount.labels(cluster.identifier, record.topic()).inc()
        val timer = analyzeLatency.startTimer()
        val backgroundJob = BackgroundJob.of(
            jobClass = javaClass.name, category = "record-analyzer", phase = "analyze",
            cluster = cluster.identifier, description = "Analyze one record"
        )
        val success = issuesRegistry.doCapturingException(backgroundJob, 60_000L) {
            analyzer.analyzeRecord(cluster, record)
        }
        timer.observeDuration()
        if (!success) {
            failedMessagesCount.labels(cluster.identifier, record.topic()).inc()
        }
    }

    @Scheduled(fixedRateString = "#{recordAnalyzerProperties.executor.trimAndDumpRate}")
    fun trimAndDump() {
        val timer = analyzeLatency.startTimer()
        val backgroundJob = BackgroundJob.of(
            category = "record-analyzer", phase = "disk-dump", description = "Trim and dump all records"
        )
        val success = issuesRegistry.doCapturingException(backgroundJob, 180_000L) {
            incrementTrimRecordsStructuresCount(analyzer.trim())
            incrementDumpRecordsStructuresCount(analyzer.dump())
        }
        timer.observeDuration()
        if (success) {
            jobExecutionsCount.labels("success").inc()
        } else {
            jobExecutionsCount.labels("fail").inc()
        }
    }
}

