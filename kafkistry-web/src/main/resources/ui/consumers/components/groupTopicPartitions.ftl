<#-- @ftlvariable name="consumerGroup" type="com.infobip.kafkistry.service.consumers.KafkaConsumerGroup" -->
<#-- @ftlvariable name="topicsOffsets" type="java.util.Map<java.lang.String, com.infobip.kafkistry.service.topic.offsets.TopicOffsets>" -->


<label class="btn btn-sm btn-outline-secondary m-3">
    <input type="checkbox" name="selectAll" style="display: none;">
    <span class="selectAll">Select all</span>
    <span class="deselectAll" style="display: none;">Unselect all</span>
</label>
<#list consumerGroup.topicMembers as topicMembers>
    <#assign topic = topicMembers.topicName>
    <div class="topic">
        <div class="m-0 mt-1 p-2 pl-3 rounded-top alert-secondary">
            <label class="mouse-pointer">
                <input type="checkbox" name="topic" data-topic="${topic}">
                ${topic}
            </label>
            <span class="collapsed pr-4 pl-4" data-target="#partitions-${topicMembers?index}" data-toggle="collapse">
                <span class="if-collapsed">▼ <small>${topicMembers.partitionMembers?size} partition(s)...</small></span>
                <span class="if-not-collapsed">△</span>
            </span>
        </div>
        <div id="partitions-${topicMembers?index}" class="collapse">
            <table class="table table-sm m-0 mb-4">
                <tr class="thead-dark">
                    <th>Partition</th>
                    <th>Topic begin</th>
                    <th>Group's current</th>
                    <th>Member client id</th>
                    <th>Topic end</th>
                    <th>Lag</th>
                </tr>
                <#list topicMembers.partitionMembers as partitionMember>
                    <tr>
                        <td>
                            <label class="mouse-pointer pr-2 width-full">
                                <input type="checkbox" name="partition"
                                       data-partition="${partitionMember.partition}">
                                ${partitionMember.partition}
                            </label>
                        </td>
                        <td>
                            ${(topicsOffsets[topic].partitionsOffsets?api.get(partitionMember.partition).begin)!"N/A"}
                        </td>
                        <td>
                            ${(partitionMember.offset)!"N/A"}
                        </td>
                        <td class="small">
                            <#if (partitionMember.member)??>
                                <span title="MemberID: ${partitionMember.member.memberId}">${partitionMember.member.clientId}</span>
                            <#else>
                                <i>(unassigned)</i>
                            </#if>
                        </td>
                        <td>
                            ${(topicsOffsets[topic].partitionsOffsets?api.get(partitionMember.partition).end)!"N/A"}
                        </td>
                        <td>
                            ${(partitionMember.lag.amount)!"N/A"}
                        </td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
</#list>
