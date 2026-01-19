let allTopics = [];

$(document).ready(function () {
    $("#compare-btn").click(compareTopics);
    let targets = $(".targets");
    targets.on("change, keypress, click, input", "input, select, button, textarea", null, compareTopics);
    targets.on("change", "input[type='radio']", null, adjustTopicConfigTypeActive);
    targets.on("click", ".swap-btn", null, swapSubject);
    targets.on("click", ".move-up-btn", null, moveSubjectUp);
    targets.on("click", ".move-down-btn", null, moveSubjectDown);
    targets.find("input[type='radio']").each(adjustTopicConfigTypeActive);
    $(".all-topics .data-topic-name").each(function () {
        allTopics.push($(this).attr("data-topic"));
    });
    $(".comparing-subject .topic-input").each(function () {
        addTopicAutocomplete($(this));
    });
    $("#add-target-btn").click(addTarget);
    targets.on("click", ".remove-subject-btn", null, removeTarget);
    adjustBaseTag();
    whenUrlSchemaReady(compareTopics);
});

function addTopicAutocomplete(subjectForm) {
    initAutocompleteInput(allTopics, subjectForm, compareTopics);
    // subjectForm
    //     .autocomplete({
    //         source: allTopics,
    //         select: compareTopics,
    //         minLength: 0
    //     })
    //     .focus(function () {
    //         $(this).data("uiAutocomplete").search($(this).val());
    //         compareTopics();
    //     });
}

function compareTopics() {
    setTimeout(doCompareTopics, 5);
}

function doCompareTopics() {
    let subjects = [];
    $(".targets .comparing-subject").each(function () {
        let subject = $(this);
        let params = extractSubjectParams(subject);
        subjects.push(params);
    });
    if (subjects.length < 1) {
        $("#compare-result").text("");
        return;
    }
    let source = subjects[0];
    let targets = subjects.slice(1);
    let request = {
        source: source,
        targets: targets
    };
    hideOpStatus()
    $
        .ajax(urlFor("compare.showCompareResult"), {
            method: "POST",
            contentType: "application/json; charset=utf-8",
            headers: {ajax: 'true'},
            data: JSON.stringify(request)
        })
        .done(function (response) {
            $("#compare-result").html(response);
            registerAllInfoTooltips();
            refreshAllConfValues();
        })
        .fail(function (error) {
            $("#compare-result").text("");
            let errorMsg = extractErrMsg(error);
            showOpError("Failed to compare", errorMsg)
        });
}

function extractSubjectParams(subject) {
    let topic = subject.find("input[name='topic']").val();
    let cluster = subject.find("select[name='cluster']").val();
    let type = subject.find("input[name='type']:checked").val();
    if (topic.trim() === "") {
        topic = null;
    }
    if (cluster === "") {
        cluster = null;
    }
    return {
        topicName: topic,
        onClusterIdentifier: cluster,
        type: type
    };
}

function setSubjectParams(subject, params) {
    subject.find("input[name='topic']").val(params.topicName);
    let clusterIdentifier = params.onClusterIdentifier;
    if (clusterIdentifier == null) {
        clusterIdentifier = "";
    }
    subject.find("select[name='cluster']").val(clusterIdentifier);
    let selectedType = subject.find("input[name='type'][value='" + params.type + "']");
    selectedType.prop("checked", true);
    adjustTopicConfigTypeActive.call(selectedType.get());
}

function moveSubjectUp() {
    let movedSubject = $(this).closest(".comparing-subject");
    moveSubject(movedSubject, -1);
}

function moveSubjectDown() {
    let movedSubject = $(this).closest(".comparing-subject");
    moveSubject(movedSubject, 1);
}

function moveSubject(movedSubject, offset) {
    let selectedIndex = movedSubject.index();
    let numSubjects = $(".targets .comparing-subject").length;
    let destinationIndex = (selectedIndex + offset + numSubjects) % numSubjects;
    let destinationSubject = $(".targets .comparing-subject:nth-child(" + (destinationIndex + 1) + ")");
    //console.log("index: " + selectedIndex + " num: " + numSubjects + " destinationIndex: " + destinationIndex);
    swapSubject(movedSubject, destinationSubject);
}

function swapSubject(subject1, subject2) {
    let params1 = extractSubjectParams(subject1);
    let params2 = extractSubjectParams(subject2);
    setSubjectParams(subject1, params2);
    setSubjectParams(subject2, params1);
    adjustBaseTag();
    compareTopics();
}

function adjustTopicConfigTypeActive() {
    let parent = $(this).closest(".comparing-subject");
    parent.find("label.btn").removeClass("active");
    parent.find("input[type='radio']:checked").closest("label.btn").addClass("active");
}

function addTarget() {
    let template = $("#subject-template").html();
    $(".targets").append(template);
    addTopicAutocomplete($(".targets .comparing-subject:last-of-type .topic-input"));
    adjustTopicConfigTypeActive.call($(".targets input[type='radio']:last-of-type"));
    adjustBaseTag();
    compareTopics();
}

function removeTarget() {
    $(this).closest(".comparing-subject").remove();
    adjustBaseTag();
    compareTopics();
}

function adjustBaseTag() {
    let baseBadges = $(".targets .base-badge");
    baseBadges.hide();
    baseBadges.first().show();
}