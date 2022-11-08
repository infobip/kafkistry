package com.infobip.kafkistry.api

import com.infobip.kafkistry.appinfo.ModuleBuildInfo
import com.infobip.kafkistry.appinfo.ModulesBuildInfoLoader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${app.http.root-path}/api/build-info")
class BuildInfoApi(
    private val buildInfoLoader: ModulesBuildInfoLoader
) {

    @GetMapping
    fun modulesBuildInfos(): List<ModuleBuildInfo> = buildInfoLoader.modulesInfos()

}