function extractErrMsg(error) {
    if (!error) {
        return "Unable to reach app server";
    }
    if (error.readyState === 4 && error.status === 0 || error.statusText === "abort") {
        return "Request was aborted";
    }
    if (error.status) {
        if (error.responseJSON && error.responseJSON.message) {
            return "HTTP " + error.status + ": " + error.responseJSON.message;
        } else {
            return "HTTP " + error.status + ": " + error.statusText;
        }
    } else {
        return "Unable to reach app server";
    }
}

function extractErrHtml(error) {
    if (error.getAllResponseHeaders().indexOf("exception-html: true") >= 0) {
        return error.responseText;
    }
    return null;
}

function jsonToYaml(object, onResult, onFailure) {
    let failureCallback = onFailure ? onFailure : function (error) {
        console.log(error);
        let errorMsg = extractErrMsg(error);
        console.log(errorMsg);
    }
    $
        .ajax("api/suggestion/json-to-yaml", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(object)
        })
        .done(onResult)
        .fail(failureCallback());
}

function yamlToJson(yamlString, onResult, onFailure) {
    let failureCallback = onFailure ? onFailure : function (error) {
        console.log(error);
        let errorMsg = extractErrMsg(error);
        console.log(errorMsg);
    }
    $
        .ajax("api/suggestion/yaml-to-json", {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            data: yamlString,
        })
        .done(onResult)
        .fail(failureCallback);
}

const JIRA_REGEX = /(^|[\/:\s,()[\]])([A-Z]+-[0-9]+)(?=$|[\s,()[\]?\/])/g;
const URL_REGEX = /(https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*))/g;
const GIT_COMMIT_REGEX = /([0-9a-f]{20,})/g;

function createLinksInText(textContainer) {
    let container = $(textContainer);
    let textRaw = container.html();

    let textWithLinks = textRaw.replace(URL_REGEX, "<a target='_blank' href='$1'>$1</a>");

    if (container.hasClass("git-links")) {
        let gitCommitBaseUrl = $("meta[name=git-commit-base-url]").attr("content");
        let gitEmbeddedBrowse = $("meta[name=git-embedded-browse]").attr("content") === 'yes';
        if (gitCommitBaseUrl) {
            let aTarget = gitEmbeddedBrowse ? "" : "target='_blank'"
            textWithLinks = textWithLinks.replace(
                GIT_COMMIT_REGEX, "<a " + aTarget + " href='" + gitCommitBaseUrl + "$1'>$1</a>"
            );
        }
    }

    let jiraBaseUrl = null;
    $("meta[name=jira-base-url]").each(function () {
        jiraBaseUrl = $(this).attr("content");
    });
    if (jiraBaseUrl) {
        textWithLinks = textWithLinks.replace(
            JIRA_REGEX, "$1<a target='_blank' href='" + jiraBaseUrl + "$2'>$2</a>"
        );
    }

    container.html(textWithLinks);
}

function extractJiraIssues(text) {
    return Array.from(text.matchAll(JIRA_REGEX), function (m) {
        return m[2];
    });
}

function extractJiraIssuesFallback(text) {
    let match = text.match(JIRA_REGEX);
    if (match === null) {
        return [];
    }
    return match.map(function (jira) {
        return jira.trim();
    });
}

function appendJiraIssuesIfAny(reasonMessage, description) {
    let jiras = String.prototype.matchAll
        ? extractJiraIssues(description)
        : extractJiraIssuesFallback(description);
    if (jiras.length === 0) {
        return reasonMessage;
    }
    return reasonMessage + " (Jira: " + jiras + ")";
}

function extractUpdateMessage() {
    return $("#update-message").val();
}

function targetBranchUriParam() {
    let value = $("input[name=targetBranch]").val();
    if (value === undefined) {
        return ""
    }
    let targetBranch = value.trim();
    if (targetBranch.length > 0) {
        return "targetBranch=" + encodeURI(targetBranch);
    }
    return "";
}


