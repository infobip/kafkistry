<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="topic" type="com.infobip.kafkistry.model.TopicDescription" -->
<#-- @ftlvariable name="existingValues" type="com.infobip.kafkistry.service.ExistingValues" -->
<#-- @ftlvariable name="topicNameProperties" type="com.infobip.kafkistry.webapp.WizardTopicNameProperties" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <script src="static/topic/createTopicWizard.js?ver=${lastCommit}"></script>
    <script src="static/presenceForm.js?ver=${lastCommit}"></script>
    <script src="static/topic/topicResourceRequirements.js?ver=${lastCommit}"></script>
    <script src="static/topic/${topicNameProperties.jsName}.js?ver=${lastCommit}"></script>
    <title>Kafkistry: Create new topic from wizard</title>
    <meta name="current-nav" content="nav-topics"/>
</head>

<body>

<#include "../commonMenu.ftl">
<#import "../common/documentation.ftl" as doc>
<#import "../common/infoIcon.ftl" as info>

<div class="container">
    <h1>Create Topic Wizard</h1>

    <hr>

    <div id="wizard-screen-1">
        <div class="card">
            <div class="card-header h4">Basic</div>
            <div class="card-body pb-0">

                <#assign nameTemplate = '<#include "${topicNameProperties.templateName}.ftl">'?interpret>
                <@nameTemplate/>
                <div class="form-group row">
                    <label class="col-sm-2 col-form-label">${topicNameProperties.nameLabel}</label>
                    <div class="col-sm-10" >
                        <span id="generatedTopicName" class="text-monospace small"><i>---</i></span>
                    </div>
                </div>

                <div class="form-group row">
                    <label class="col-sm-2 col-form-label">Presence</label>
                    <div id="presence" class="col-sm-10 p-1">
                        <#assign presence = topic.presence>
                        <#include "../common/presenceForm.ftl">
                    </div>
                </div>
            </div>
        </div>
        <br/>

        <div class="card">
            <div class="card-header h4">Metadata</div>
            <div class="card-body pb-0">
                <div class="form-group row">
                    <label class="col-sm-2 col-form-label" for="owner-name">Owner</label>
                    <input id="owner-name" type="text" name="teamName" placeholder="Which team is owner of the topic..." value="${topic.owner}"
                           class="form-control col-sm-10" />
                    <div id="owners" style="display: none;">
                        <#list existingValues.owners as owner>
                            <span>${owner}</span>
                        </#list>
                    </div>
                </div>
                <div class="form-group row">
                    <label class="col-sm-2 col-form-label" for="description-text">Description</label>
                    <textarea id="description-text" class="form-control col-sm-10" name="purpose" rows="5"
                              placeholder="Describe what is in the topic, specify some Jira task like ABC-123...">${topic.description}</textarea>
                </div>
                <div class="form-group row">
                    <label class="col-sm-2 col-form-label" for="producer-name">Producer</label>
                    <input id="producer-name" type="text" name="producer" placeholder="Which service is producing to topic..."
                           value="${topic.producer}" class="form-control col-sm-10">
                    <div id="producers" style="display: none;">
                        <#list existingValues.producers as producer>
                            <span>${producer}</span>
                        </#list>
                    </div>
                </div>
            </div>
        </div>

    </div>

    <div id="wizard-screen-2" style="display: none">
        <div class="card">
            <div class="card-header h4">Expected metrics</div>
            <div class="card-body pb-0">
                <#if topic.resourceRequirements??>
                    <#assign resourceRequirements = topic.resourceRequirements>
                </#if>
                <#include "form/topicResourceRequirements.ftl">
            </div>
        </div>
        <br/>

        <div class="card">
            <div class="card-header h4">High Availability</div>
            <div class="card-body p-0">
                <div class="ha-container">
                    <table class="table m-0">
                        <thead class="thead-light">
                        <tr>
                            <th>
                                <div class="form-row">
                                    <div class="col"></div>
                                    <div class="col-">Select mode</div>
                                </div>
                            </th>
                            <th class="p-1">
                                <label class="m-0 btn btn-outline-secondary form-control">
                                    <input type="radio" name="highAvailability" value="NONE"> None
                                </label>
                            </th>
                            <th class="p-1">
                                <label class="m-0 btn btn-outline-info form-control active">
                                    <input type="radio" name="highAvailability" value="BASIC" checked> Basic
                                </label>
                            </th>
                            <th class="p-1">
                                <label class="m-0 btn btn-outline-primary form-control">
                                    <input type="radio" name="highAvailability" value="STRONG_AVAILABILITY"> Availability
                                </label>
                            </th>
                            <th class="p-1">
                                <label class="m-0 btn btn-outline-primary form-control">
                                    <input type="radio" name="highAvailability" value="STRONG_DURABILITY"> Durability
                                </label>
                            </th>
                        </tr>
                        </thead>
                        <tbody class="font-italic">
                        <tr>
                            <td>Replication factor</td>
                            <td class="ha-none"><code>1</code></td>
                            <td class="ha-basic"><code>2</code></td>
                            <td class="ha-available"><code>3</code></td>
                            <td class="ha-durable"><code>3</code></td>
                        </tr>
                        <tr>
                            <td>min.insync.replicas</td>
                            <td class="ha-none"><code>1</code></td>
                            <td class="ha-basic"><code>1</code></td>
                            <td class="ha-available"><code>1</code></td>
                            <td class="ha-durable"><code>2</code></td>
                        </tr>
                        <tr>
                            <td>Producing: allowed # brokers down</td>
                            <td class="ha-none">0</td>
                            <td class="ha-basic">1</td>
                            <td class="ha-available">2</td>
                            <td class="ha-durable">1</td>
                        </tr>
                        <tr>
                            <td>Consuming: allowed # brokers down</td>
                            <td class="ha-none">0</td>
                            <td class="ha-basic">1</td>
                            <td class="ha-available">2</td>
                            <td class="ha-durable">2</td>
                        </tr>
                        <tr>
                            <td>One broker down (maintenance/incident) - Can consume?</td>
                            <td class="ha-none"><span class="badge badge-danger">no</span></td>
                            <td class="ha-basic"><span class="badge badge-success">yes</span></td>
                            <td class="ha-available"><span class="badge badge-success">yes</span></td>
                            <td class="ha-durable"><span class="badge badge-success">yes</span></td>
                        </tr>
                        <tr>
                            <td>One broker down (maintenance/incident) - Can produce?</td>
                            <td class="ha-none"><span class="badge badge-danger">no</span></td>
                            <td class="ha-basic"><span class="badge badge-success">yes</span></td>
                            <td class="ha-available"><span class="badge badge-success">yes</span></td>
                            <td class="ha-durable"><span class="badge badge-success">yes</span></td>
                        </tr>
                        <tr>
                            <td>Two brokers down (maintenance+incident) - Can consume?</td>
                            <td class="ha-none"><span class="badge badge-danger">no</span></td>
                            <td class="ha-basic"><span class="badge badge-danger">no</span></td>
                            <td class="ha-available"><span class="badge badge-success">yes</span></td>
                            <td class="ha-durable"><span class="badge badge-success">yes</span></td>
                        </tr>
                        <tr>
                            <td>Two brokers down (maintenance+incident) - Can produce?</td>
                            <td class="ha-none"><span class="badge badge-danger">no</span></td>
                            <td class="ha-basic"><span class="badge badge-danger">no</span></td>
                            <td class="ha-available"><span class="badge badge-success">yes</span></td>
                            <td class="ha-durable"><span class="badge badge-danger">no</span></td>
                        </tr>
                        <#assign minimalDataLossInfoMsg>
                            Might loose only messages that were produced just prior to broker failure,
                            but not yet replicated to second replica
                        </#assign>
                        <#assign completeDataLossInfoMsg>
                            No replication means that all partitions that were hosted on broker that failed are completely gone
                        </#assign>
                        <#assign noDataLossInfoMsg>
                            <code>min.insync.replicas = 2</code> allows producing only if at least 2 replicas are online,
                            meaning that each produced message will be persisted on at least 2 brokers allowing 1 broker to fail permanently
                        </#assign>
                        <tr>
                            <td>One broker's host permanent HW failure causes data-loss?</td>
                            <td class="ha-none">
                                <span class="badge badge-danger">yes</span>
                                <@info.icon tooltip=completeDataLossInfoMsg/>
                            </td>
                            <td class="ha-basic">
                                <span class="badge badge-warning">minimal</span>
                                <@info.icon tooltip=minimalDataLossInfoMsg/>
                            </td>
                            <td class="ha-available">
                                <span class="badge badge-warning">minimal</span>
                                <@info.icon tooltip=minimalDataLossInfoMsg/>
                            </td>
                            <td class="ha-durable">
                                <span class="badge badge-success">no</span>
                                <@info.icon tooltip=noDataLossInfoMsg/>
                            </td>
                        </tr>
                        <tr>
                            <td>Storage disk usage</td>
                            <td class="ha-none"><span class="badge badge-success">1x</span></td>
                            <td class="ha-basic"><span class="badge badge-warning">2x</span></td>
                            <td class="ha-available"><span class="badge badge-danger">3x</span></td>
                            <td class="ha-durable"><span class="badge badge-danger">3x</span></td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </div>
    <br/>

    <div>
        <#include "../common/cancelBtn.ftl">
        <button id="wizard-prev-btn" class="btn btn-outline-primary btn-sm" style="display: none;">&larr; Previous</button>
        <button id="wizard-next-btn" class="btn btn-outline-primary btn-sm" style="display: inline;">Next &rarr;</button>
        <br/>
        <br/>
        <button id="wizard-go-to-create-btn" class="btn btn-primary btn-sm" style="display: none;">
            Suggest topic configuration... <@info.icon tooltip=doc.wizardGoToCreateTopic/>
        </button>
    </div>

    <#include "../common/serverOpStatus.ftl">
    <br/>

</div>

<#include "../common/pageBottom.ftl">
</body>
</html>