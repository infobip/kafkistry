package com.infobip.kafkistry.webapp.security

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.VIEW_DATA
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorityAuthorizationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.stereotype.Component

interface RequestAuthorizationPermissionsConfigurer {

    fun configure(registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry) {
        registry.configureWith()
    }

    fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry.configureWith() = Unit

    fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry.antMatchers(
        vararg antPatterns: String,
    ): AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl = antMatchers(method = null, *antPatterns)

    fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry.antMatchers(
        method: HttpMethod? = null,
        vararg antPatterns: String,
    ): AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl = run {
        val matchers = antPatterns.map { AntPathRequestMatcher(it, method?.name()) }
        requestMatchers(*matchers.toTypedArray())
    }

    fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry.allMethodsExcept(
        httpMethod: HttpMethod,
        antPattern: String,
        configurer: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl.() -> Unit
    ) {
        HttpMethod.values().filter { it != httpMethod }.forEach { configurer(antMatchers(it, antPattern)) }
    }

    fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl.hasAuthority(
        authority: UserAuthority, description: String? = null,
    ) {
        val desc = description ?: authority.description
        if (desc != null) {
            val authManager = AuthorityAuthorizationManager.hasAuthority<RequestAuthorizationContext>(authority.authority)
            access(DescribedAuthorizationManager(authManager, desc))
        } else {
            hasAuthority(authority.authority)
        }
    }

}

abstract class AbstractRequestAuthorizationPermissionsConfigurer : RequestAuthorizationPermissionsConfigurer {

    @Autowired
    protected lateinit var httpProperties: WebHttpProperties
    protected lateinit var rootPath: String

    @PostConstruct
    private fun init() {
        rootPath = httpProperties.rootPath
    }
}

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1000)
class DefaultsAuthorizationConfigurer : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry.configureWith() {
        antMatchers("$rootPath/**").hasAuthority(VIEW_DATA)
        anyRequest().fullyAuthenticated()
    }
}
