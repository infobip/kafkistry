package com.infobip.kafkistry.slack.filter

import com.infobip.kafkistry.audit.AuditEvent
import com.infobip.kafkistry.autopilot.AutopilotUser
import com.infobip.kafkistry.slack.SlackProperties
import com.infobip.kafkistry.webapp.security.User
import org.springframework.stereotype.Component

@Component
class AuditEventFilterFactory(
    private val slackProperties: SlackProperties,
    private val autopilotUser: AutopilotUser,
) {

    fun createFilter(): AuditEventFilter {
        return with(slackProperties.filter) {
            val filters = buildList {
                add(autopilotFilter(includeAutopilot))
            }.filterNotNull()
            AuditEventFilter.allOf(filters)
        }
    }

    private fun autopilotFilter(include: Boolean): AuditEventFilter? {
        return if (include) null else ExcludeAutopilotFilter(autopilotUser.user)
    }

}

private class ExcludeAutopilotFilter(private val autopilot: User) : AuditEventFilter {
    override fun accepts(auditEvent: AuditEvent): Boolean {
        val isAutopilot = auditEvent.user == autopilot
        return !isAutopilot
    }

}