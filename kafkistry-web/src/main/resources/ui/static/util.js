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

/**
 * Validates the targetBranch field value.
 * Returns null if valid, or an error message string if invalid.
 *
 * Valid values when JIRA validation is enabled:
 * - Main branch name (from backend config, e.g., "master" or "main")
 * - A string containing a valid JIRA key (e.g., "ABC-123", "feature/ABC-456")
 *
 * Valid values when JIRA validation is disabled:
 * - Empty string (uses default branch)
 * - Main branch name (from backend config)
 * - Any other branch name
 */
function validateTargetBranch() {
    let targetBranchInput = $("input[name=targetBranch]");

    // If the input doesn't exist (git storage not enabled), validation passes
    if (targetBranchInput.length === 0) {
        return null;
    }

    let value = targetBranchInput.val();
    if (value === undefined || value === null) {
        value = "";
    }

    let targetBranch = value.trim();

    // Get configuration from backend
    let branchRequiredJiraKey = $("meta[name='git-branch-required-jira-key']").attr("content") === "yes";
    let mainBranch = $("meta[name='git-main-branch']").attr("content") || "master";

    // Check if branch is empty
    if (targetBranch.length === 0) {
        if (branchRequiredJiraKey) {
            return "Target branch must be '" + mainBranch + "' or contain a valid JIRA key (e.g., ABC-123)";
        }
        // Empty is valid when JIRA validation is disabled (uses default branch)
        return null;
    }

    // Main branch is always valid (case-sensitive)
    if (targetBranch === mainBranch) {
        return null;
    }

    if (branchRequiredJiraKey) {
        // Check if it contains a valid JIRA key using existing JIRA_REGEX
        let jiraMatches = targetBranch.match(JIRA_REGEX);
        if (jiraMatches && jiraMatches.length > 0) {
            return null;  // Contains a JIRA key, valid
        }

        // If we get here, it's invalid
        return "Target branch must be '" + mainBranch + "' or contain a valid JIRA key (e.g., ABC-123)";
    }

    // If JIRA key validation is not required, any non-empty branch name is valid
    return null;
}


