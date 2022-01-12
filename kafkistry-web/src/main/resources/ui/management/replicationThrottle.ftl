<#-- @ftlvariable name="maxBrokerIOBytes" type="java.lang.Long" -->

<div class="input-group form-inline col-6 m-0 p-0">
    <label for="throttlePerSec-input mr-2">
        IO throttle per broker (leader and follower):
    </label>
    <input type="number" id="throttlePerSec-input" value="5" class="ml-2 form-control"/>
    <div class="input-group-append">
        <select id="throttleUnit-input" class="form-control alert-secondary" title="throttle rate unit">
            <option selected>MB/sec</option>
            <option>kB/sec</option>
            <option>B/sec</option>
        </select>
    </div>
</div>
<span class="m-1"><br/></span>
<span>Sync complete ETA:
    <span id="reassign-eta" data-max-io-bytes="${(maxBrokerIOBytes?c)!'0'}">
        ---
    </span>
    <small id="reassign-eta-explain"></small>
    <br/>
    <small><i>(ETA is considering only existing data on disk without new produced traffic)</i></small>
</span>
<br/>
