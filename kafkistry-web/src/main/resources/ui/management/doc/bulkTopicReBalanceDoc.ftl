<div>
    <button role="button" data-toggle="collapse" data-target="#re-balance-doc"
            class="btn btn-sm btn-outline-info mb-2">
        Topic balance documentation...
    </button>
    <div id="re-balance-doc" class="collapse">
        <ul>
            <li>Goal is to have each topic being "balanced" on it's own</li>
            <li>"Balanced" topic means it has:
                <ul>
                    <li>equal number of partition replicas per each broker (equally use broker(s) disk and equally contribute to broker's I/O load)</li>
                    <li>equal number of partition leaders per each broker (consumers and producers equally contribute to all brokers I/O load)</li>
                </ul>
            </li>
            <li>Balance is achieved by taking topic's current assignment and finding minimal re-assignment to apply
                <ul>
                    <li>find minimal set of replicas to "move"</li>
                    <li>find minimal set of partitions to change its leader replica</li>
                </ul>
            </li>
            <li>
                Performance impact:
                <ul>
                    <li>Moving replicas between brokers may use significant amount of I/O and affect (increased latency) producers and consumers</li>
                    <li>You may want to use appropriate I/O throttling to limit speed of replication when broker(s)
                        start to pull whole partition's data from other brokers for all partitions that are newly being assigned to that broker</li>
                </ul>
            </li>
            <#if (bulkReBalanceDoc??)>
                <li>
                    <strong>NOTE</strong> when doing bulk re-balance on multiple topics
                    <ul>
                        <li>You may want to limit number of re-assignments doing at once to reduce stress on cluster</li>
                        <li>Big number of re-assigned topic-partitions by itself can also have impact on cluster (regardless of how much underlying data all those partition have)</li>
                    </ul>
                </li>
                <li>
                    Strategy when having large number of "dis-balanced" topics:
                    <ul>
                        <li>Select a "batch" of topics to re-balance at once</li>
                        <li>Wait for re-assignments to complete and do verify re-assignments completion (which will remove generated throttles)</li>
                        <li>Repeat with new batch of topics until all topics are "balanced"</li>
                    </ul>
                </li>
            </#if>
        </ul>
    </div>
</div>
