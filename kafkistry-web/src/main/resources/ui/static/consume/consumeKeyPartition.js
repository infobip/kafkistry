$(document).ready(function () {
    $("#resolve-partition-for-key-btn").click(resolvePartitionForKey);
    $("#determine-partition-btn").click(function () {
       $("#determine-partition-form").slideToggle(100,"linear");
    });
});

function resolvePartitionForKey() {
    let readData = readFormData();
    let key = $("input[name=messageKey]").val();
    let serializer = $("select[name=keySerializer]").val();
    let resultContainer = $("#resolved-partition-for-key-result");
    showOpProgressOnId("resolve-partition", "Resolving...");
    $
        .get("api/consume/partition-of-key" +
            "?clusterIdentifier=" + encodeURIComponent(readData.clusterIdentifier) +
            "&topicName=" + encodeURIComponent(readData.topicName) +
            "&key=" + encodeURIComponent(key) +
            "&serializerType=" + serializer
        )
        .done(function (partition) {
            console.log("resolved partition: "+partition);
            resultContainer.text("Partition: " + partition);
            resultContainer.show();
            hideServerOpOnId("resolve-partition");
            addPartitionToFilter(partition, readData);
        })
        .fail(function(error){
            let errorMsg = extractErrMsg(error);
            console.log("Failed to resolve:" + errorMsg);
            resultContainer.hide();
            showOpErrorOnId("resolve-partition", "Failed to resolve partition", errorMsg);
        });
}

function addPartitionToFilter(partition, readData) {
    let partitionsFilter = readData.readConfig.partitions ? readData.readConfig.partitions : [];
    if (partitionsFilter.indexOf(partition) < 0) {
        partitionsFilter.push(partition);
    }
    $("input[name=partitions]").val(partitionsFilter.join(","));
}