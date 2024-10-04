<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="imageProps"  type="com.infobip.kafkistry.webapp.ImageProperties" -->
<#-- @ftlvariable name="gitStorageEnabled"  type="java.lang.Boolean" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry</title>
    <meta name="current-nav" content="nav-home"/>
    <script src="static/home/home.js?ver=${lastCommit}"></script>
</head>

<body>

<#include "../commonMenu.ftl">

<div class="container">
    <div class="text-center pt-2 form-row">
        <div class="col">
            <h1>
                <span class="align-middle">
                    <img class="p-1 mb-1" src="static/img/${imageProps.dirPath}/${imageProps.banner}?ver=${lastCommit}" alt="kr" style="width: 50%"/>
                </span>
            </h1>
        </div>
    </div>

    <hr/>
    <h3 class="text-center">Your owned Status counts / quick navigate</h3>

    <div class="form-row">
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <@_util.yourOwned what="topics"/>
                    <span class="h5">Topics</span>
                </div>
                <div class="card-body p-0" id="topics-your-stats-container">
                    <#assign statusId = "topics-your-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <@_util.yourOwned what="consumer groups"/>
                    <span class="h5">Consumers</span>
                </div>
                <div class="card-body p-0" id="consumer-groups-your-stats-container">
                    <#assign statusId = "consumer-groups-your-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <@_util.yourOwned what="principal ACLs"/>
                    <span class="h5">ACLs</span>
                </div>
                <div class="card-body p-0" id="acls-your-stats-container">
                    <#assign statusId = "acls-your-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
    </div>

    <hr/>
    <h3 class="text-center">All Status counts / quick navigate</h3>

    <div class="form-row mb-2">
        <div class="col-4">
            <div class="card">
                <div class="card-header">
                    <span class="h5">Clusters</span>
                </div>
                <div class="card-body p-0" id="clusters-stats-container">
                    <#assign statusId = "clusters-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
        <div class="col-8">
            <div class="card">
                <div class="card-header">
                    <span class="h5">Tags</span>
                </div>
                <div class="card-body p-0" id="tags-stats-container" style="max-height: 400px !important; overflow-y: auto;">
                    <#assign statusId = "tags-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
    </div>
    <div class="form-row">
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <span class="h5">All Topics</span>
                </div>
                <div class="card-body p-0" id="topics-stats-container">
                    <#assign statusId = "topics-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <span class="h5">All Consumers</span>
                </div>
                <div class="card-body p-0" id="consumer-groups-stats-container">
                    <#assign statusId = "consumer-groups-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
        <div class="col">
            <div class="card">
                <div class="card-header">
                    <span class="h5">All ACLs</span>
                </div>
                <div class="card-body p-0" id="acls-stats-container">
                    <#assign statusId = "acls-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
            <div class="card mt-2">
                <div class="card-header">
                    <span class="h5">All Quotas</span>
                </div>
                <div class="card-body p-0" id="quotas-stats-container">
                    <#assign statusId = "quotas-stats">
                    <#include "../common/serverOpStatus.ftl">
                </div>
            </div>
        </div>
    </div>

    <#if gitStorageEnabled>
        <hr/>
        <h3 class="text-center">Pending repository updates</h3>
        <div id="pending-requests-container">
            <#assign statusId = "pending-requests">
            <#include "../common/serverOpStatus.ftl">
        </div>
    </#if>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>