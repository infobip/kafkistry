<#import "../common/infoIcon.ftl" as estInfo>
<#assign topicSizeEstimateDoc>
    Number of messages estimate is computed as <code>endOffset - beginOffset</code>.<br/>
    Such estimation works well in most scenarios but actual number of available messages to consume
    will be <i>less or equal</i> than estimated number.<br/>
    <strong>NOTE</strong>: causes for wrong estimation:
    <ul>
        <li>
            Transactional / idempotent producing will <i>use</i> topic partition's offsets as
            transaction markers causing estimated number to be <i>slightly</i> bigger than actual
            number depending on how many offsets are <i>spent</i> on transactional markers and
            how many messages are produced but not being transactionally committed
        </li>
        <li>
            <code>compact</code>-ed topics will make compaction based on message key, estimate in this case
            might be orders-of-magnitude bigger than actual number of messages
        </li>
    </ul>
</#assign>
<@estInfo.icon tooltip=topicSizeEstimateDoc/>