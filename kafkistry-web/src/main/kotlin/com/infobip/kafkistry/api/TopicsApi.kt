package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import com.infobip.kafkistry.service.UpdateContext
import org.springframework.web.bind.annotation.*

/**
 * CRUD on managed topics repository
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/topics")
class TopicsApi(
    private val topicsRegistry: TopicsRegistryService
) {

    @PostMapping
    fun createTopic(
        @RequestBody topicDescription: TopicDescription,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = topicsRegistry.createTopic(topicDescription, UpdateContext(message, targetBranch))

    @DeleteMapping
    fun deleteTopic(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = topicsRegistry.deleteTopic(topicName, UpdateContext(message, targetBranch))

    @PutMapping
    fun updateTopic(
        @RequestBody topicDescription: TopicDescription,
        @RequestParam("message") message: String,
        @RequestParam(name = "targetBranch", required = false) targetBranch: String?,
    ): Unit = topicsRegistry.updateTopic(topicDescription, UpdateContext(message, targetBranch))

    @GetMapping("/single")
    fun getTopic(
        @RequestParam("topicName") topicName: TopicName
    ): TopicDescription = topicsRegistry.getTopic(topicName)

    @GetMapping("/single/changes")
    fun getTopicChanges(
        @RequestParam("topicName") topicName: TopicName
    ): List<TopicRequest> = topicsRegistry.getTopicChanges(topicName)

    @GetMapping
    fun listTopics(): List<TopicDescription> = topicsRegistry.listTopics()

    @GetMapping("/pending-requests")
    fun pendingTopicsRequests(): Map<TopicName, List<TopicRequest>> = topicsRegistry.findAllPendingRequests()

    @GetMapping("/single/pending-requests")
    fun pendingTopicRequests(
        @RequestParam("topicName") topicName: TopicName
    ): List<TopicRequest> = topicsRegistry.findPendingRequests(topicName)

    @GetMapping("/single/pending-requests/branch")
    fun pendingTopicRequest(
        @RequestParam("topicName") topicName: TopicName,
        @RequestParam("branch") branch: String
    ): TopicRequest = topicsRegistry.pendingRequest(topicName, branch)

    @GetMapping("/history")
    fun commitsHistory(): List<ChangeCommit<TopicChange>> = topicsRegistry.getCommitsHistory()

}