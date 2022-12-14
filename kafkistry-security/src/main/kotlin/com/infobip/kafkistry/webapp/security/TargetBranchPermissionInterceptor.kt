package com.infobip.kafkistry.webapp.security

import com.infobip.kafkistry.service.KafkistryPermissionException
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class TargetBranchPermissionInterceptor(
        private val currentRequestUserResolver: CurrentRequestUserResolver,
        private val branchProperties: ProtectedBranchProperties
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        currentRequestUserResolver.resolveUser()?.run {
            request.parameterMap["targetBranch"]?.forEach {
                checkTargetBranchPermission(it, this)
            }
        }
        return true
    }

    private fun checkTargetBranchPermission(
            targetBranch: String, user: User
    ) {
        if (targetBranch != branchProperties.name) {
            return
        }
        val permittedByAuthority = user.role.authorities.any {
            it.name == branchProperties.permittedAuthorityName
        }
        if (!permittedByAuthority) {
            throw KafkistryPermissionException("Not allowed to write directly to '$targetBranch' branch")
        }
    }

}

@Component
@ConfigurationProperties("app.security.git.protected-write-branch")
class ProtectedBranchProperties {
    var name: String = "master"
    var permittedAuthorityName: String? = null
}