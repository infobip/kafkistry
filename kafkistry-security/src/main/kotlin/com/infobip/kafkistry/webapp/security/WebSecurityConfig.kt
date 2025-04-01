package com.infobip.kafkistry.webapp.security

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.security.auth.preauth.PreAuthUserResolver
import com.infobip.kafkistry.webapp.security.auth.preauth.PreAuthenticatedUserProcessingFiler
import com.infobip.kafkistry.webapp.security.auth.providers.KafkistryAuthProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import org.springframework.security.web.header.HeaderWriterFilter
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConfigurationProperties("app.security")
class WebSecurityProperties {
    var enabled = true
    var csrfEnabled = true
    var explainDeniedAccess = true
    var deniedAccessHelpMessage = "Please contact your Kafkistry administrator!"
}

@Configuration
class WebSecurityConfig(
    private val authConfiguration: AuthenticationConfiguration,
    private val currentRequestReadingFilter: CurrentRequestReadingFilter,
    private val unauthorizedEntryPoint: UnauthorizedEntryPoint,
    private val preAuthUserResolvers: List<PreAuthUserResolver>,
    private val authProviders: List<KafkistryAuthProvider>,
    private val authorizationConfigurers: List<RequestAuthorizationPermissionsConfigurer>,
    private val noSessionRequestMatchers: Optional<List<NoSessionRequestMatcher>>,
    private val httpProperties: WebHttpProperties,
    private val securityProperties: WebSecurityProperties,
    private val sessionRegistry: SessionRegistry,
) {

    @Bean
    fun authenticationManager(): AuthenticationManager = authConfiguration.authenticationManager

    @Bean
    fun preAuthUserFilter(): PreAuthenticatedUserProcessingFiler {
        val filter = PreAuthenticatedUserProcessingFiler(preAuthUserResolvers)
        filter.setAuthenticationManager(authenticationManager())
        return filter
    }

    @Autowired
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        authProviders.sortedBy { it.order }.forEach {
            auth.authenticationProvider(it)
        }
    }

    @Bean
    fun httpSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        //picked HeaderWriterFilter because it's early in filter chain
        http.addFilterBefore(currentRequestReadingFilter, HeaderWriterFilter::class.java)
        if (securityProperties.enabled) {
            with(http) {
                sessionManagement {
                    it
                        .sessionFixation().migrateSession()
                        .maximumSessions(10)
                        .sessionRegistry(sessionRegistry)
                }
                addFilterBefore(preAuthUserFilter(), AbstractPreAuthenticatedProcessingFilter::class.java)
                if (securityProperties.explainDeniedAccess) {
                    configureExplainingDenyAccess()
                }
                authorizeHttpRequests { configurer ->
                    authorizationConfigurers.forEach { it.configure(configurer) }
                }
                formLogin { it.loginPage("${httpProperties.rootPath}/login") }
                logout { it.logoutUrl("${httpProperties.rootPath}/logout") }
                csrf {
                    if (!securityProperties.csrfEnabled) {
                        it.disable()
                    } else {
                        noSessionRequestMatchers.ifPresent { matchers ->
                            it.ignoringRequestMatchers(*matchers.toTypedArray())
                        }
                    }
                }
                httpBasic {}
                exceptionHandling {
                    it
                        .defaultAuthenticationEntryPointFor(unauthorizedEntryPoint, unauthorizedEntryPoint.apiCallMatcher())
                        .accessDeniedHandler(unauthorizedEntryPoint)
                }
            }
        } else {
            with(http) {
                authorizeHttpRequests { it.anyRequest().permitAll() }
                csrf { it.disable() }
            }
        }
        return http.build()
    }

    private fun HttpSecurity.configureExplainingDenyAccess() {
        authorizeHttpRequests { configurer ->
            configurer.withObjectPostProcessor(object : ObjectPostProcessor<AuthorizationFilter> {
                override fun <O : AuthorizationFilter> postProcess(authFilter: O): O {
                    @Suppress("UNCHECKED_CAST")
                    return AuthorizationFilter(
                        ExplainingDenyExceptionAuthorizationManager(
                            authFilter.authorizationManager, securityProperties.deniedAccessHelpMessage
                        )
                    ) as O
                }
            })
        }
    }

}

