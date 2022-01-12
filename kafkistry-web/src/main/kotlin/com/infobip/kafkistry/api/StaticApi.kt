package com.infobip.kafkistry.api

import com.infobip.kafkistry.webapp.url.AppUrl
import com.infobip.kafkistry.webapp.url.Url
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/static")
class StaticApi(
       private val appUrl: AppUrl
) {

    @GetMapping("/url-schema")
    fun urlSchema(): Map<String, Url> = appUrl.schema()
}