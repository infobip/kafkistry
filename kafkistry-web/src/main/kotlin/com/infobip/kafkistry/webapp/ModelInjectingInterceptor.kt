package com.infobip.kafkistry.webapp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import freemarker.ext.beans.BeansWrapper
import freemarker.template.Configuration
import com.infobip.kafkistry.api.BackgroundIssuesApi
import com.infobip.kafkistry.appinfo.ModulesBuildInfoLoader
import com.infobip.kafkistry.autopilot.config.AutopilotRootProperties
import com.infobip.kafkistry.repository.config.GitBrowseProperties
import com.infobip.kafkistry.repository.config.GitRepositoriesProperties
import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.webapp.jira.JiraProperties
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import com.infobip.kafkistry.webapp.security.WebSecurityProperties
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class CompositeRequestInterceptor(
        private val interceptors: List<ModelInjectingInterceptor>
) : HandlerInterceptor {

    override fun postHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any, modelAndView: ModelAndView?) {
        if (modelAndView == null) {
            return
        }
        injectModel(modelAndView, request)
    }

    fun injectModel(modelAndView: ModelAndView, request: HttpServletRequest) {
        interceptors.forEach { it.inject(modelAndView, request) }
    }
}

interface ModelInjectingInterceptor {

    fun inject(modelAndView: ModelAndView, request: HttpServletRequest)

}

@Component
class UserModelInjectingInterceptor(
        private val userResolver: CurrentRequestUserResolver
) : ModelInjectingInterceptor {

    override fun inject(modelAndView: ModelAndView, request: HttpServletRequest) {
        userResolver.resolveUser()?.also {
            modelAndView.addObject("kafkistryUser", it)
        }
    }
}

@Component
class RequestInjectingInterceptor : ModelInjectingInterceptor {

    override fun inject(modelAndView: ModelAndView, request: HttpServletRequest) {
        modelAndView.addObject("request", request)
        request.getAttribute("_csrf")?.also { modelAndView.addObject("_csrf", it) }
    }
}

@Component
class StaticsInjectingInterceptor : ModelInjectingInterceptor {

    private val json = jacksonObjectMapper()

    override fun inject(modelAndView: ModelAndView, request: HttpServletRequest) {
        val w = BeansWrapper(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
        modelAndView.addObject("statics", w.staticModels)
        modelAndView.addObject("enums", w.enumModels)
        modelAndView.addObject("json", json)
    }
}

@Component
class PropertiesInjectingInterceptor(
    buildInfoLoader: ModulesBuildInfoLoader,
    private val appUrl: AppUrl,
    private val issuesApi: BackgroundIssuesApi,
    private val hostnameResolver: HostnameResolver,
    private val securityProperties: WebSecurityProperties,
    private val autopilotProperties: AutopilotRootProperties,
    private val gitRepositoriesProperties: GitRepositoriesProperties?,
    private val gitBrowseProperties: GitBrowseProperties?,
    private val webUIProperties: WebUIProperties,
    private val jiraProperties: JiraProperties,
) : ModelInjectingInterceptor {

    //prevent browser caching stale js/css resources when new build is deployed
    private val latestBuildCommit = buildInfoLoader.modulesInfos().maxByOrNull { it.git.commit.time }
        ?.git?.commit?.id?.abbrev
        ?: "NONE"

    override fun inject(modelAndView: ModelAndView, request: HttpServletRequest) {
        with(modelAndView) {
            addObject("appUrl", appUrl)
            appUrl.visitExtraUrls { name, baseUrls ->
                addObject("appUrl_$name", baseUrls)
            }
            jiraProperties.baseUrl.ifNotBlank { addObject("jiraBaseUrl", it) }
            webUIProperties.customJsScriptsCsv.ifNotBlank {
                addObject("customJsScripts", it.split(',').filter(String::isNotBlank))
            }
            addObject("imageProps", webUIProperties.image)
            gitBrowseProperties?.branchBaseUrl.ifNotBlank { addObject("gitBranchBaseUrl", it) }
            gitBrowseProperties?.commitBaseUrl.ifNotBlank {
                addObject("gitCommitBaseUrl", it)
                addObject("gitEmbeddedBrowse", false)
            } ?: run {
                addObject("gitCommitBaseUrl", appUrl.git().showCommit(""))
                addObject("gitEmbeddedBrowse", true)
            }
            addObject("autopilotEnabled", autopilotProperties.enabled)
            addObject("gitStorageEnabled", gitRepositoriesProperties?.enabled ?: false)
            addObject("securityEnabled", securityProperties.enabled)
            addObject("backgroundJobIssueGroups", issuesApi.currentGroupedIssues())
            addObject("hostname", hostnameResolver.hostname)
            addObject("lastCommit", latestBuildCommit)
            addObject("forceTagForPresence", webUIProperties.forceTagForPresence)
        }
    }

    private fun <R> String?.ifNotBlank(operation: (String) -> R) = this?.takeIf { it.isNotBlank() }?.let(operation)
}