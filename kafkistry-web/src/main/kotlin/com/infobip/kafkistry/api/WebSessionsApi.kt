package com.infobip.kafkistry.api

import com.infobip.kafkistry.webapp.UserSessions
import com.infobip.kafkistry.webapp.UserSessionsAndStats
import com.infobip.kafkistry.webapp.UserSessionsService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${app.http.root-path}/api/web-sessions")
class WebSessionsApi(
    private val sessionsService: UserSessionsService,
) {

    @GetMapping
    fun allUsersSessions(): List<UserSessions> = sessionsService.currentUsersSessions()

    @GetMapping("/stats")
    fun allUsersSessionsAndStats(): UserSessionsAndStats = sessionsService.currentUsersSessionsAndStats()

    @DeleteMapping("/{session-id}/expire")
    fun expireSession(
        @PathVariable("session-id") sessionId: String,
    ): Unit = sessionsService.expireSession(sessionId)

}