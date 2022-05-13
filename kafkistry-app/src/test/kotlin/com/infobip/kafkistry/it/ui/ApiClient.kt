package com.infobip.kafkistry.it.ui

import io.kotlintest.matchers.fail
import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.TopicStatuses
import com.infobip.kafkistry.service.consumers.ClusterConsumerGroups
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection

class ApiClient(
        private val host: String,
        private val port: Int,
        private val rootPath: String,
        private vararg val cookies: String
) {

    private val rest = RestTemplate(object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }).also {
        it.interceptors.add(ClientHttpRequestInterceptor { request: HttpRequest, bytes: ByteArray, execution: ClientHttpRequestExecution ->
            if (cookies.isNotEmpty()) {
                request.headers.addAll("Cookie", cookies.toMutableList())
            }
            execution.execute(request, bytes)
        })
    }

    fun url(path: String) = "http://$host:$port$rootPath/$path"

    fun getPage(path: String): Document {
        return getPageOrNull(path) ?: fail("Expected to successfully download $path")
    }

    fun postPage(path: String, body: Any?): Document {
        return postPageOrNull(path, body) ?: fail("Expected to successfully download $path")
    }

    fun getPageOrNull(path: String): Document? {
        val url = url(path)
        return rest.getForObject(url, String::class.java)?.let { html ->
            Jsoup.parse(html, url)
        }
    }

    fun postPageOrNull(path: String, body: Any?): Document? {
        val url = url(path)
        return rest.postForObject(url, body, String::class.java)?.let { html ->
            Jsoup.parse(html, url)
        }
    }

    fun getContent(path: String): String? {
        val url = url(path)
        return rest.getForObject(url, String::class.java)
    }

    fun <R> apiGet(path: String, response: Class<R>):R? {
        return rest.getForObject(url(path), response)
    }

    fun <R> apiPost(path: String, body: Any?, response: Class<R>): R? {
        return rest.postForObject(url(path), body, response)
    }

    fun apiDelete(path: String) {
        return rest.delete(url(path))
    }

    fun addTopic(topic: TopicDescription) {
        rest.postForObject(url("/api/topics?message=test-msg"), topic, String::class.java)
    }

    fun addCluster(cluster: KafkaCluster) {
        rest.postForObject(url("/api/clusters?message=test-msg"), cluster, String::class.java)
    }

    fun getTopic(topicName: TopicName): TopicDescription {
        return rest.getForObject(url("/api/topics/single?topicName={name}"), TopicDescription::class.java, topicName)!!
    }

    fun listAllTopics(): List<TopicDescription> {
        return rest.getForObject(url("/api/topics"), TopicDescriptions::class.java)!!
    }

    fun listAllClusters(): List<KafkaCluster> {
        return rest.getForObject(url("/api/clusters"), KafkaClusters::class.java)!!
    }

    fun inspectAllTopics(): List<TopicStatuses> {
        return rest.getForObject(url("/api/inspect/topics"), TopicStatusesList::class.java)!!
    }

    fun inspectTopicUpdateDryRun(topic: TopicDescription): TopicStatuses {
        return rest.postForObject(url("/api/inspect/topic-inspect-dry-run"), topic, TopicStatuses::class.java)!!
    }

    fun deleteTopic(name: TopicName) {
        rest.delete(url("/api/topics?topicName=${name}&message=test-msg"))
    }

    fun deleteCluster(identifier: KafkaClusterIdentifier) {
        rest.delete(url("/api/clusters?clusterIdentifier=${identifier}&message=test-msg"))
    }

    fun listClusterConsumerGroups(clusterIdentifier: KafkaClusterIdentifier): ClusterConsumerGroups {
        return rest.getForObject(url("/api/consumers/clusters/{cluster}"), ClusterConsumerGroups::class.java, clusterIdentifier)!!
    }

    fun deleteClusterConsumerGroup(clusterIdentifier: KafkaClusterIdentifier, consumerGroupId: ConsumerGroupId) {
        rest.delete(url("/api/consumers/clusters/{cluster}/groups/{consumerGroup}"), clusterIdentifier, consumerGroupId)
    }

    fun refreshClusters() {
        rest.postForObject(url("/api/clusters/refresh"), null, String::class.java)
    }

    fun testClusterConnection(
        connectionString: String,
        ssl: Boolean = false, sasl: Boolean = false,
        profiles: List<KafkaProfile> = emptyList()
    ): ClusterInfo {
        return rest.getForObject(url("/api/clusters/test-connection?connectionString=${connectionString}&ssl=$ssl&sasl=$sasl&profiles=${profiles.joinToString(",")}"), ClusterInfo::class.java)!!
    }

    fun createMissingTopic(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
        val params = LinkedMultiValueMap<String, String>().also {
            it.add("topicName", topicName)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject(url("/api/management/create-missing-topic"), params, String::class.java)
    }

    fun electPreferredLeaders(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
       val params = LinkedMultiValueMap<String, String>().also {
            it.add("topicName", topicName)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject(url("/api/management/run-preferred-replica-elections"), params, String::class.java)
    }

    fun suggestDefaultTopicDescription(): TopicDescription {
        return rest.getForObject(url("/api/suggestion/create-default-topic"), TopicDescription::class.java)!!
    }

    fun suggestObjectToYaml(obj: Any): String {
        return rest.postForObject(url("/api/suggestion/json-to-yaml"), obj, String::class.java)!!
    }

    fun verifyTopicReAssignment(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier): String {
        return rest.getForObject(
                url("/api/management/verify-topic-partitions-reassignment?clusterIdentifier=$clusterIdentifier&topicName=$topicName"),
                String::class.java
        )!!
    }

    fun submitWizardAnswers(answers: TopicCreationWizardAnswers) {
        rest.postForObject(url("/api/topic-wizard/submit-answers"), answers, String::class.java)
    }

    fun createPrincipalAcls(principalAcls: PrincipalAclRules) {
        rest.postForObject(url("/api/acls?message=test-msg"), principalAcls, String::class.java)
    }

    fun getPrincipalAcls(principal: PrincipalId): PrincipalAclRules {
        return rest.getForObject(url("/api/acls/single?principal={principal}"), PrincipalAclRules::class.java, principal)!!
    }

    fun listPrincipalsAcls(): PrincipalsAclsList {
        return rest.getForObject(url("/api/acls"), PrincipalsAclsList::class.java)!!
    }

    fun createEntityQuotas(quotaDescription: QuotaDescription) {
        rest.postForObject(url("/api/quotas?message=test-msg"), quotaDescription, String::class.java)
    }

    fun getEntityQuotas(entityID: QuotaEntityID): QuotaDescription {
        return rest.getForObject(url("/api/quotas/single?quotaEntityID={entityID}"), QuotaDescription::class.java, entityID)!!
    }

    fun listQuotaEntities(): EntityQuotasList {
        return rest.getForObject(url("/api/quotas"), EntityQuotasList::class.java)!!
    }

    fun createMissingEntityQuotas(entityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier) {
        val params = LinkedMultiValueMap<String, String>().also {
            it.add("quotaEntityID", entityID)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject(url("/api/quotas-management/create-quotas"), params, String::class.java)
    }


    class KafkaClusters : ArrayList<KafkaCluster>()
    class TopicDescriptions : ArrayList<TopicDescription>()
    class TopicStatusesList : ArrayList<TopicStatuses>()
    class PrincipalsAclsList : ArrayList<PrincipalAclRules>()
    class EntityQuotasList : ArrayList<QuotaDescription>()

}