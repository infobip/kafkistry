<style>
    .reset-options td {
        width: auto;
    }

    .reset-options td.min {
        width: 1%;
        white-space: nowrap;
    }

    .reset-options .legend-highlight {
        color: red;
        background-color: #b3d5ae;
    }
</style>


<div class="card reset-options">
    <div class="card-header">
        <div class="h4 m-0">Choose reset options</div>
    </div>
    <div class="card-body">
        <div class="form-row">

            <div class="col-">
                <select name="resetType" class="form-control" title="Reference type">
                    <option value="EARLIEST">To beginning (earliest)</option>
                    <option value="LATEST">To end (latest)</option>
                    <option value="TIMESTAMP">At timestamp</option>
                    <option value="RELATIVE">Relative seek</option>
                    <option value="EXPLICIT">To offset</option>
                </select>
            </div>

            <div class="begin-option reset-option col-" style="display: none;">plus</div>
            <div class="begin-option reset-option col-2" style="display: none;">
                <input type="number" name="begin-offset" value="0" class="form-control" title="seek by">
            </div>

            <div class="end-option reset-option col-" style="display: none;">minus</div>
            <div class="end-option reset-option col-2" style="display: none;">
                <input type="number" name="end-offset" value="0" class="form-control" title="seek by">
            </div>

            <div class="timestamp-option reset-option col-3" style="display: none;">
                <input type="number" name="timestamp" class="form-control" title="seek to timestamp">
                <span id="picked-time-indicator" class="time text-primary small">---</span>
            </div>
            <div class="timestamp-option reset-option col" style="display: none;">
                <i>(seek to offset of first record with greater or equal timestamp, NOTE: using your browser timezone in date/time picker)</i>
            </div>

            <div class="relative-option reset-option col-" style="display: none;">
                current consumer <br/>position shifted by
            </div>
            <div class="relative-option reset-option col-2" style="display: none;">
                <input type="number" name="relative-offset" value="0" class="form-control" title="seek by">
            </div>
            <div class="relative-option reset-option col-" style="display: none;">
                <i>(positive=skip forward, negative=rewind backward)</i>
            </div>

            <div class="explicit-option reset-option col-" style="display: none;">
                with offset value of
            </div>
            <div class="explicit-option reset-option col-2" style="display: none;">
                <input type="number" name="explicit-offset" class="form-control" title="offset">
            </div>
            <div class="explicit-option reset-option col-" style="display: none;">
                <i>(makes sense to use only when targeting ony single partition)</i>
            </div>
        </div>

        <div class="row">
            <table class="table table-borderless table-sm small mt-4 mb-0">
                <tr class="no-hover">
                    <th colspan="10">Help legend:</th>
                </tr>
                <tr class="no-hover">
                    <td class="min text-left type type-begin">begin</td>
                    <td class="min text-left type type-begin-n">begin+N</td>
                    <td></td>
                    <td class="min text-center type type-explicit">Explicit</td>
                    <td></td>
                    <td class="min text-center type type-timestamp">&gt;=Timestamp</td>
                    <td></td>
                    <td class="min text-center type type-current-mn">current-N</td>
                    <td class="min text-center type type-current">current</td>
                    <td class="min text-center type type-current-pn">current+N</td>
                    <td></td>
                    <td class="min text-right type type-end-n">end-N</td>
                    <td class="min text-right type type-end">end</td>
                </tr>
                <tr class="bg-warning">
                    <td class="text-left type type-begin">|</td>
                    <td class="text-left type type-begin-n">|</td>
                    <td></td>
                    <td class="text-center type type-explicit">|</td>
                    <td></td>
                    <td class="text-center type type-timestamp">|</td>
                    <td></td>
                    <td class="text-center type type-current-mn">|</td>
                    <td class="text-center type type-current">|</td>
                    <td class="text-center type type-current-pn">|</td>
                    <td></td>
                    <td class="text-right type type-end-n">|</td>
                    <td class="text-right type type-end">|</td>
                </tr>
            </table>
        </div>
    </div>
</div>
