package com.infobip.kafkistry.webapp

import freemarker.core.HTMLOutputFormat
import freemarker.template.TemplateExceptionHandler
import freemarker.template.Version
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.http.CacheControl
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.*
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver
import java.util.*
import java.util.concurrent.TimeUnit

@Component
@ConfigurationProperties("app.ui")
class WebUIProperties {
    var caching = true
    var customJsScriptsCsv = ""
    var forceTagForPresence = false

    @NestedConfigurationProperty
    val image = ImageProperties()
}

class ImageProperties {
    var dirPath = "default"
    var logo = "logo.png"
    var banner = "banner.png"
    var icon = "icon.png"
}

@Configuration
@EnableWebMvc
@CrossOrigin(origins = ["*"])
class WebAppConfig(
        private val interceptors: List<HandlerInterceptor>,
        httpProperties: WebHttpProperties,
        uiProperties: WebUIProperties,
) : WebMvcConfigurer {

    private val rootPath = httpProperties.rootPath
    private val caching = uiProperties.caching

    override fun configureViewResolvers(registry: ViewResolverRegistry) {
        registry.freeMarker()
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        with(registry) {
            addViewController("$rootPath/login").setViewName("login")
            addRedirectViewController("/", "$rootPath/home").setKeepQueryParams(false)
            addRedirectViewController(rootPath, "$rootPath/home").setKeepQueryParams(false)
        }
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val resourceLocations = if (caching) {
            listOf("classpath:/ui/static/")
        } else {
            //for faster development
            resolveFileResourcesFor("classpath*:/ui/static/")
        }
        registry
                .addResourceHandler("$rootPath/static/**")
                .addResourceLocations(*resourceLocations.toTypedArray())
                .setCacheControl(
                        if (caching) CacheControl.maxAge(10, TimeUnit.MINUTES)
                        else CacheControl.noCache()
                )
    }

    @Bean
    fun freemarkerConfigurer(): FreeMarkerConfigurer {
        return FreeMarkerConfigurer().apply {
            val properties = Properties().apply {
                setProperty("url_escaping_charset", "UTF-8")
                setProperty("template_update_delay",
                        if (caching) Int.MAX_VALUE.toString()
                        else "0"
                )
                setProperty("api_builtin_enabled", "true")
            }
            setFreemarkerSettings(properties)
            val resourceLocations = if (caching) {
                listOf("classpath:/ui/")
            } else {
                //for faster development
                resolveFileResourcesFor("classpath*:/ui/")
            }
            setTemplateLoaderPaths(*resourceLocations.toTypedArray())
            setPreferFileSystemAccess(!caching)
            afterPropertiesSet()
            //to allow accessing map entries by non-string key
            configuration.incompatibleImprovements = Version("2.3.22")
            //configuration.recognizeStandardFileExtensions = true
            configuration.outputFormat = HTMLOutputFormat.INSTANCE
            configuration.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        }
    }

    @Bean
    fun freemarkerViewResolver(): FreeMarkerViewResolver {
        return FreeMarkerViewResolver().apply {
            isCache = caching
            setSuffix(".ftl")
            setContentType("text/html;charset=UTF-8")
            order = Ordered.HIGHEST_PRECEDENCE
        }
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        interceptors.forEach { registry.addInterceptor(it) }
    }

    private fun resolveFileResourcesFor(classPath: String): List<String> {
        return PathMatchingResourcePatternResolver().getResources(classPath)
                .map { it.url.toString() }
                .map { it.replace("target/classes", "src/main/resources")}
    }

}
