$(document).ready(function () {
    $("#apply-throttling-brokers-btn").click(applyThrottlingBrokers)
});

let LEADERS_PROGRESS = "leader-brokers";
let THROTTLED_PROGRESS = "throttled-brokers";
let TOPICS_PROGRESS = "topics";

async function applyThrottlingBrokers() {
    $("#throttle-setup-progress").show();

    let throttleBytesPerSec = getThrottleBytesPerSec();

    let unThrottledBrokers = extractBrokerIds($("#leader-brokers .broker-id"));
    let throttledBrokers = extractBrokerIds($("#throttled-brokers .broker-id"));
    let offlineBrokers = extractBrokerIds($("#offline-brokers .broker-id"));
    let topicsThrottles = extractTopicsThrottles();

    showOpProgressOnId(LEADERS_PROGRESS, "Pending...");
    showOpProgressOnId(TOPICS_PROGRESS, "Pending...");
    showOpProgressOnId(THROTTLED_PROGRESS, "Pending...");

    await applyBrokersThrottle(
        unThrottledBrokers, offlineBrokers, throttleBytesPerSec, LEADERS_PROGRESS, {leaderRate: throttleBytesPerSec}
    );

    await applyTopicsThrottle(topicsThrottles);

    await applyBrokersThrottle(
        throttledBrokers, offlineBrokers, throttleBytesPerSec, THROTTLED_PROGRESS, {followerRate: throttleBytesPerSec}
    );

    $("#back-to-cluster-btn").show();
}

async function applyBrokersThrottle(
    brokerIds, offlineBrokers, throttleBytesPerSec,
    statusId, throttle
) {
    let clusterIdentifier = $("meta[name=clusterIdentifier]").attr("content");
    let unThrottledBrokersStatus = "";
    let hasSkipped = false;
    let hasFailed = false;
    for (let i = 0; i < brokerIds.length; i++) {
        await sleep(100);
        let brokerId = brokerIds[i];
        if (offlineBrokers.indexOf(brokerId) >= 0) {
            unThrottledBrokersStatus += "Broker " + brokerId + ": skipped due to being offline\n";
            hasSkipped = true;
            continue;
        }
        let resultError = null;
        showOpProgressOnId(statusId, "Setting throttle for broker " + brokerId + " (" + (i + 1) + " of " + brokerIds.length + ")");
        await setBrokerThrottle(clusterIdentifier, brokerId, throttle, (error) => {
            resultError = error;
        });

        if (resultError == null) {
            unThrottledBrokersStatus += "Broker " + brokerId + ": successfully set throttle rate\n";
        } else {
            hasFailed = true;
            unThrottledBrokersStatus += "Broker " + brokerId + ": failed to set throttle rate: " + resultError + "\n";
        }
    }
    if (hasSkipped || hasFailed) {
        showOpErrorOnId(statusId, "Completed with issues", unThrottledBrokersStatus);
    } else {
        showOpSuccessOnId(statusId, "Completed successfully", unThrottledBrokersStatus);
    }
}

async function applyTopicsThrottle(topicsThrottles) {
    let clusterIdentifier = $("meta[name=clusterIdentifier]").attr("content");
    let topics = Object.keys(topicsThrottles);
    let hasFailed = false;
    for (let i = 0; i < topics.length; i++) {
        let topic = topics[i];
        let topicProgressId = "topic-" + topic;
        let throttleConfig = topicsThrottles[topic];

        showOpProgressOnId(topicProgressId, "Setting throttle...");
        showOpProgressOnId(TOPICS_PROGRESS, "Setting throttle for topic '" + topic + "' (" + (i + 1) + " of " + topics.length + ")");

        let resultError = null;
        await setTopicThrottle(clusterIdentifier, topic, throttleConfig, (error) => {
            resultError = error;
        });

        if (resultError == null) {
            showOpSuccessOnId(topicProgressId, "Successfully set throttle");
        } else {
            hasFailed = true;
            showOpErrorOnId(topicProgressId, "Failed to set throttle", resultError);
        }
    }

    if (hasFailed) {
        showOpErrorOnId(TOPICS_PROGRESS, "Completed with issues");
    } else {
        showOpSuccessOnId(TOPICS_PROGRESS, "Completed successfully");
    }
}


async function setBrokerThrottle(clusterIdentifier, brokerId, throttle, errorCallback) {
    console.log("Setting throttle for broker " + brokerId + ", throttle: " + JSON.stringify(throttle));
    return await $
        .ajax("api/clusters-management/throttle/" + brokerId + "?clusterIdentifier=" + encodeURI(clusterIdentifier), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(throttle)
        })
        .done(function () {
            errorCallback(null);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            errorCallback(errorMsg);
        });
}

async function setTopicThrottle(clusterIdentifier, topic, topicConfig, errorCallback) {
    console.log("Setting throttle for topic " + topic + ", throttle: " + JSON.stringify(topicConfig));
    return await $
        .ajax("api/management/set-topic-config"
            + "?clusterIdentifier=" + encodeURI(clusterIdentifier)
            + "&topicName=" + encodeURI(topic), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(topicConfig)
        })
        .done(function () {
            errorCallback(null);
        })
        .fail(function (error) {
            let errorMsg = extractErrMsg(error);
            errorCallback(errorMsg);
        });
}

function extractBrokerIds(selectedElements) {
    return selectedElements
        .map((i, broker) => {
            return $(broker).attr("data-brokerId");
        })
        .get();
}

function extractTopicsThrottles() {
    let topicThrottles = {};
    $(".topic-throttle").each(function () {
        let topicElement = $(this);
        let topic = topicElement.attr("data-topic");
        let configs = {};
        topicElement.find(".config-entry").each(function () {
            let entry = $(this);
            let key = entry.attr("data-configKey");
            configs[key] = entry.attr("data-configValue");
        });
        topicThrottles[topic] = configs;
    });
    return topicThrottles;
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}


