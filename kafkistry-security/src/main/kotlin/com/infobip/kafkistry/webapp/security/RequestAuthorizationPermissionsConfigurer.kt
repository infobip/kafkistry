package com.infobip.kafkistry.webapp.security

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.VIEW_DATA
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

interface RequestAuthorizationPermissionsConfigurer {

    fun configure(registry: ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry) {
        registry.configureWith()
    }

    fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() = Unit

    fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.allMethodsExcept(
        httpMethod: HttpMethod,
        antPattern: String,
        configurer: ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl.() -> Unit
    ) {
        HttpMethod.values().filter { it != httpMethod }.forEach { configurer(antMatchers(it, antPattern)) }
    }

    fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl.hasAuthority(authority: UserAuthority) {
        hasAuthority(authority.authority)
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

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        antMatchers("$rootPath/**").hasAuthority(VIEW_DATA)
        anyRequest().fullyAuthenticated()
    }
}
