package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.ExistingValues
import com.infobip.kafkistry.service.existingvalues.ExistingValuesService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/values")
class ExistingValuesApi(
    private val existingValuesService: ExistingValuesService
) {

    @GetMapping
    fun all(): ExistingValues = existingValuesService.allExistingValues()

}