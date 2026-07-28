package com.infobip.kafkistry.it.ui

import com.infobip.kafkistry.api.exception.ApiError
import io.kotlintest.matchers.fail
import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.topic.TopicStatuses
import com.infobip.kafkistry.service.consumers.ClusterConsumerGroups
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import java.net.HttpURLConnection

class ApiClient(
        private val host: String,
        private val port: Int,
        private val rootPath: String,
        private vararg val cookies: String
) {

    val rest = RestTemplate(object : SimpleClientHttpRequestFactory() {
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

    fun getPageError(path: String): Document {
        return try {
            val response = getPage(path)
            fail("Expected to fail on '$path', but got response: $response")
        } catch (ex: Exception) {
            assertThat(ex).isInstanceOf(RestClientResponseException::class.java)
            val error = ex as RestClientResponseException
            val body = error.getResponseBodyAs(String::class.java)
                ?: fail("Got null body from '$path' having error $ex")
            Jsoup.parse(body, path)
        }
    }

    fun getApiPageError(path: String): ApiError {
        return try {
            val response = getPage(path)
            fail("Expected to fail on '$path', but got response: $response")
        } catch (ex: Exception) {
            assertThat(ex).isInstanceOf(RestClientResponseException::class.java)
            val error = ex as RestClientResponseException
            error.getResponseBodyAs(ApiError::class.java)
                ?: fail("Got null body from '$path' having error $ex")
        }
    }

    fun postPage(path: String, body: Any?): Document {
        return postPageOrNull(path, body) ?: fail("Expected to successfully download $path")
    }

    fun getPageOrNull(path: String): Document? {
        val url = url(path)
        return rest.getForObject<String>(url)?.let { html ->
            Jsoup.parse(html, url)
        }
    }

    fun postPageOrNull(path: String, body: Any?): Document? {
        val url = url(path)
        return rest.postForObject<String>(url, body)?.let { html ->
            Jsoup.parse(html, url)
        }
    }

    fun getContent(path: String): String? {
        val url = url(path)
        return rest.getForObject<String>(url)
    }

    inline fun <reified R : Any> apiGet(path: String): R? {
        return rest.getForObject<R>(url(path))
    }

    inline fun <reified R : Any> apiPost(path: String, body: Any?): R? {
        return rest.postForObject<R>(url(path), body)
    }

    fun apiDelete(path: String) {
        return rest.delete(url(path))
    }

    fun addTopic(topic: TopicDescription) {
        rest.postForObject<String>(url("/api/topics?message=test-msg"), topic)
    }

    fun addCluster(cluster: KafkaCluster) {
        rest.postForObject<String>(url("/api/clusters?message=test-msg"), cluster)
    }

    fun getTopic(topicName: TopicName): TopicDescription {
        return rest.getForObject<TopicDescription>(url("/api/topics/single?topicName={name}"), topicName)!!
    }

    fun listAllTopics(): List<TopicDescription> {
        return rest.getForObject<TopicDescriptions>(url("/api/topics"))!!
    }

    fun listAllClusters(): List<KafkaCluster> {
        return rest.getForObject<KafkaClusters>(url("/api/clusters"))!!
    }

    fun inspectAllTopics(): List<TopicStatuses> {
        return rest.getForObject<TopicStatusesList>(url("/api/inspect/topics"))!!
    }

    fun inspectTopicUpdateDryRun(topic: TopicDescription): TopicStatuses {
        return rest.postForObject<TopicStatuses>(url("/api/inspect/topic-inspect-dry-run"), topic)!!
    }

    fun deleteTopic(name: TopicName) {
        rest.delete(url("/api/topics?topicName=${name}&message=test-msg"))
    }

    fun deleteCluster(identifier: KafkaClusterIdentifier) {
        rest.delete(url("/api/clusters?clusterIdentifier=${identifier}&message=test-msg"))
    }

    fun listClusterConsumerGroups(clusterIdentifier: KafkaClusterIdentifier): ClusterConsumerGroups {
        return rest.getForObject<ClusterConsumerGroups>(url("/api/consumers/clusters/{cluster}"), clusterIdentifier)!!
    }

    fun deleteClusterConsumerGroup(clusterIdentifier: KafkaClusterIdentifier, consumerGroupId: ConsumerGroupId) {
        rest.delete(url("/api/consumers/clusters/{cluster}/groups/{consumerGroup}"), clusterIdentifier, consumerGroupId)
    }

    fun refreshClusters() {
        rest.postForObject<String>(url("/api/clusters/refresh"), null)
    }

    fun testClusterConnection(
        connectionString: String,
        ssl: Boolean = false, sasl: Boolean = false,
        profiles: List<KafkaProfile> = emptyList()
    ): ClusterInfo {
        return rest.getForObject<ClusterInfo>(url("/api/clusters/test-connection?connectionString=${connectionString}&ssl=$ssl&sasl=$sasl&profiles=${profiles.joinToString(",")}"))!!
    }

    fun createMissingTopic(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
        val params = LinkedMultiValueMap<String, String>().also {
            it.add("topicName", topicName)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject<String>(url("/api/management/create-missing-topic"), params)
    }

    fun electPreferredLeaders(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier) {
       val params = LinkedMultiValueMap<String, String>().also {
            it.add("topicName", topicName)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject<String>(url("/api/management/run-preferred-replica-elections"), params)
    }

    fun suggestDefaultTopicDescription(): TopicDescription {
        return rest.getForObject<TopicDescription>(url("/api/suggestion/create-default-topic"))!!
    }

    fun suggestObjectToYaml(obj: Any): String {
        return rest.postForObject<String>(url("/api/suggestion/json-to-yaml"), obj)!!
    }

    fun verifyTopicReAssignment(topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier): String {
        return rest.getForObject<String>(
                url("/api/management/verify-topic-partitions-reassignment?clusterIdentifier=$clusterIdentifier&topicName=$topicName")
        )!!
    }

    fun submitWizardAnswers(answers: TopicCreationWizardAnswers) {
        rest.postForObject<String>(url("/api/topic-wizard/submit-answers"), answers)
    }

    fun createPrincipalAcls(principalAcls: PrincipalAclRules) {
        rest.postForObject<String>(url("/api/acls?message=test-msg"), principalAcls)
    }

    fun getPrincipalAcls(principal: PrincipalId): PrincipalAclRules {
        return rest.getForObject<PrincipalAclRules>(url("/api/acls/single?principal={principal}"), principal)!!
    }

    fun listPrincipalsAcls(): PrincipalsAclsList {
        return rest.getForObject<PrincipalsAclsList>(url("/api/acls"))!!
    }

    fun createEntityQuotas(quotaDescription: QuotaDescription) {
        rest.postForObject<String>(url("/api/quotas?message=test-msg"), quotaDescription)
    }

    fun getEntityQuotas(entityID: QuotaEntityID): QuotaDescription {
        return rest.getForObject<QuotaDescription>(url("/api/quotas/single?quotaEntityID={entityID}"), entityID)!!
    }

    fun listQuotaEntities(): EntityQuotasList {
        return rest.getForObject<EntityQuotasList>(url("/api/quotas"))!!
    }

    fun createMissingEntityQuotas(entityID: QuotaEntityID, clusterIdentifier: KafkaClusterIdentifier) {
        val params = LinkedMultiValueMap<String, String>().also {
            it.add("quotaEntityID", entityID)
            it.add("clusterIdentifier", clusterIdentifier)
        }
        rest.postForObject<String>(url("/api/quotas-management/create-quotas"), params)
    }


    class KafkaClusters : ArrayList<KafkaCluster>()
    class TopicDescriptions : ArrayList<TopicDescription>()
    class TopicStatusesList : ArrayList<TopicStatuses>()
    class PrincipalsAclsList : ArrayList<PrincipalAclRules>()
    class EntityQuotasList : ArrayList<QuotaDescription>()

}