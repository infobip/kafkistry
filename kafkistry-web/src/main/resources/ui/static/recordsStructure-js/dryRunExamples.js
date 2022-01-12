function addExamplesOptions() {
    let exampleSelect = $("select[name=example]");
    inputExamples.forEach(function (example) {
        exampleSelect.append("<option>" + example.name + "</option>");
    });
    exampleSelect.change(showExample);
}

function showExample() {
    let exampleName = $("select[name=example]").val();
    let example = inputExamples.find(function (example) {
        return example.name === exampleName;
    });
    if (!example) {
        return;
    }
    editor.setValue(example.content);
    $("select[name=inputMode] option[value=" + example.mode + "]").prop("selected", true);
    $("select[name=encoding] option[value=" + example.encoding + "]").prop("selected", true);
    adjustInputMode();
}

let inputExamples = [
    {
        name: "Single record",
        encoding: "UTF8_STRING",
        mode: "SINGLE",
        content: JSON.stringify({
            id: "hc34ct54t-4ct5t-56y53ey-w45tc4cw4-c4c4w-4tc4wgw45fw4t5y5veh-v56ev",
            width: 100,
            height: 50,
            tags: ["foo", "bar"],
            timestamp: new Date().getTime(),
            person: {
                name: "Namey",
                role: "ADMIN",
                active: true,
            }
        }, null, 2)
    },
    {
        name: "Multiple records",
        encoding: "UTF8_STRING",
        mode: "MULTI",
        content: Array.from(Array(30).keys()).map(function (index) {
            let object = {
                "id": index,
                "type": (index % 2 === 0) ? "EVEN" : "ODD",
                "seenAt": (index % 7 === 0) ? new Date().getTime() : undefined,
                "delivered": (index % 3 === 0) ? true : ((index % 3 === 1) ? false : null),
            };
            return JSON.stringify(object);
        }).join("\n")
    }
];