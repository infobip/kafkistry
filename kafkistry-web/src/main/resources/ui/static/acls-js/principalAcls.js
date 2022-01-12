$(document).ready(function () {
    $("input[name=breakdown]").change(adjustBreakdownTypeVisibility);
    adjustBreakdownTypeVisibility.call($("input[name=breakdown]:checked").get());
    loadPrincipalAclsYaml();
});

function loadPrincipalAclsYaml() {
    let principalAclsYaml = $("#principal-yaml");
    let principal = principalAclsYaml.attr("data-principal");
    $.get("api/acls/single", {principal: principal})
        .done(function (principalAcls) {
            jsonToYaml(principalAcls, function (yaml) {
                $("#principal-filename").text("acls/" + principalAcls.principal.replace(/[^\w\-.]/, "_") + ".yaml");
                principalAclsYaml.text(yaml);
            });
        });
}

function adjustBreakdownTypeVisibility() {
    let selectedVal = $(this).val();
    if (selectedVal === "cluster-rules") {
        $("#rule-clusters").hide();
        $("#cluster-rules").show();
    } else {
        $("#cluster-rules").hide();
        $("#rule-clusters").show();
    }
    $(this).closest("div ").find("label.btn").removeClass("active");
    $(this).closest("label.btn").addClass("active");
}
