package com.infobip.kafkistry.kafka

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.NetworkException
import org.apache.kafka.common.errors.TimeoutException
import com.infobip.kafkistry.kafka.config.KafkaManagementClientProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class KafkaClientProvider(
    private val clientFactory: ClientFactory,
    private val properties: KafkaManagementClientProperties,
) : AutoCloseable {

    private var closed = false

    override fun close() {
        closed = true
        clientPoolsCache.invalidateAll()
    }

    fun perClusterConcurrency(): Int = properties.clusterConcurrency

    private val clientPoolsCache: LoadingCache<KafkaCluster, KafkaClientPool> = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .removalListener(RemovalListener<KafkaCluster, KafkaClientPool> { notification ->
            notification.value?.close()
        })
        .build(object : CacheLoader<KafkaCluster, KafkaClientPool>() {
            override fun load(key: KafkaCluster): KafkaClientPool = GenericObjectPool(
                KafkaManagementClientFactory(
                    connectionDefinition = key.connectionDefinition(),
                    clientFactory = clientFactory
                ),
                GenericObjectPoolConfig<KafkaManagementClient>().apply {
                    maxTotal = properties.clusterConcurrency
                    testWhileIdle = true
                    timeBetweenEvictionRunsMillis = TimeUnit.MINUTES.toMillis(5)
                }
            )
        })

    fun <R> doWithClient(kafkaCluster: KafkaCluster, operation: (KafkaManagementClient) -> R): R {
        if (closed) {
            throw KafkistryIllegalStateException("This kafka clients provider is already closed, can't access client for cluster '${kafkaCluster.identifier}'")
        }
        val pool = clientPoolsCache.get(kafkaCluster)
        val client = pool.borrowObject()
        var invalidated = false
        return try {
            operation(client)
        } catch (ex: KafkaException) {
            when (ex) {
                is TimeoutException,
                is NetworkException -> pool.invalidateObject(client).also {
                    invalidated = true
                }
            }
            throw ex
        } finally {
            if (!invalidated) {
                pool.returnObject(client)
            }
        }
    }

    fun <R> doWithNewClient(connectionDefinition: ConnectionDefinition, operation: (KafkaManagementClient) -> R): R {
        return clientFactory.createManagementClient(connectionDefinition).use(operation)
    }

}

private typealias KafkaClientPool = GenericObjectPool<KafkaManagementClient>

private class KafkaManagementClientFactory(
    private val connectionDefinition: ConnectionDefinition,
    private val clientFactory: ClientFactory
) : BasePooledObjectFactory<KafkaManagementClient>() {

    override fun wrap(obj: KafkaManagementClient) = DefaultPooledObject(obj)

    override fun create(): KafkaManagementClient = clientFactory.createManagementClient(connectionDefinition)

    override fun destroyObject(p: PooledObject<KafkaManagementClient>) = p.`object`.close()

    override fun validateObject(p: PooledObject<KafkaManagementClient>): Boolean {
        return try {
            p.`object`.test()
            true
        } catch (ex: Exception) {
            false
        }
    }

}