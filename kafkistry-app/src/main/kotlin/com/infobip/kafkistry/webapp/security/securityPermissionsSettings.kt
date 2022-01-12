package com.infobip.kafkistry.webapp.security

import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.sql.config.SQLProperties
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.MANAGE_CONSUMERS
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.MANAGE_GIT
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.MANAGE_KAFKA
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.READ_TOPIC
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.REQUEST_ACL_UPDATES
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.REQUEST_CLUSTER_UPDATES
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.REQUEST_QUOTA_UPDATES
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.REQUEST_TOPIC_UPDATES
import com.infobip.kafkistry.webapp.security.UserAuthority.Companion.VIEW_DATA
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
@Order(0)
class StaticResourcesAuthorizationConfigurer(
    private val metricsProperties: PrometheusMetricsProperties,
) : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        antMatchers(
            "$rootPath/login",
            "$rootPath/static/**",
            "$rootPath${metricsProperties.httpPath}",
            "$rootPath/api/static/**"
        ).permitAll()
    }
}

@Component
@Order(100)
class ApiGetResourcesAuthorizationConfigurer : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        antMatchers(HttpMethod.GET, "$rootPath/api/clusters/**").hasAuthority(VIEW_DATA)
        antMatchers(HttpMethod.GET, "$rootPath/api/topics/**").hasAuthority(VIEW_DATA)
        antMatchers(HttpMethod.GET, "$rootPath/api/acls/**").hasAuthority(VIEW_DATA)
    }
}

@Component
@Order(200)
class ApiNonGetResourcesAuthorizationConfigurer : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/clusters/**") { hasAuthority(REQUEST_CLUSTER_UPDATES) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/topics/**") { hasAuthority(REQUEST_TOPIC_UPDATES) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/topic-wizard/**") { hasAuthority(REQUEST_TOPIC_UPDATES) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/acls/**") { hasAuthority(REQUEST_ACL_UPDATES) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/quotas/**") { hasAuthority(REQUEST_QUOTA_UPDATES) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/git/**") { hasAuthority(MANAGE_GIT) }
        allMethodsExcept(HttpMethod.GET, "$rootPath/api/consumers/**") { hasAuthority(MANAGE_CONSUMERS) }
    }
}

@Component
@Order(300)
class ApiAuthorizationConfigurer : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        antMatchers("$rootPath/api/inspect/**").hasAuthority(VIEW_DATA)
        antMatchers("$rootPath/api/management/**").hasAuthority(MANAGE_KAFKA)
        antMatchers("$rootPath/api/acls-management/**").hasAuthority(MANAGE_KAFKA)
        antMatchers("$rootPath/api/quotas-management/**").hasAuthority(MANAGE_KAFKA)
        antMatchers("$rootPath/api/clusters-management/**").hasAuthority(MANAGE_KAFKA)
        antMatchers("$rootPath/api/consume/**").hasAuthority(READ_TOPIC)
        antMatchers("$rootPath/consume/**").hasAuthority(READ_TOPIC)
    }
}

@Component
@Order(301)
class ClickhouseSQLNoSessionAuthorizationConfigurer(
    sqlProperties: SQLProperties
) : AbstractRequestAuthorizationPermissionsConfigurer(), NoSessionRequestMatcher {

    private val enabled = sqlProperties.clickHouse.openSecurity

    private val matcher: RequestMatcher by lazy {
        AntPathRequestMatcher("$rootPath/api/sql/click-house")
    }

    override fun matches(request: HttpServletRequest) = enabled && matcher.matches(request)

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        if (enabled) {
            requestMatchers(matcher).permitAll()
        }
    }
}

@Component
@Order(400)
class UsesSessionsAuthorizationConfigurer : AbstractRequestAuthorizationPermissionsConfigurer() {

    override fun ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry.configureWith() {
        antMatchers("$rootPath/api/web-sessions/**").hasRole(UserRole.ADMIN.name)
        antMatchers("$rootPath/about/users-sessions").hasRole(UserRole.ADMIN.name)
    }
}
