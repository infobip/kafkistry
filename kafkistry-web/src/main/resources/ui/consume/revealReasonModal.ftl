<div class="modal fade" id="reveal-reason-modal" tabindex="-1" role="dialog" aria-labelledby="reveal-reason-modal-title" aria-hidden="true">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="reveal-reason-modal-title">Show sensitive data</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p class="small mb-2">
                    Re-reads this record from Kafka with masking bypassed. This action is audit-logged
                    (user, cluster, topic, partition, offset, reason). Provide a reason explaining
                    <em>why</em> you need to see the unmasked values.
                </p>
                <div id="reveal-reason-target" class="small text-muted mb-2"></div>
                <label for="reveal-reason-input" class="form-label">Reason</label>
                <textarea id="reveal-reason-input"
                          class="form-control"
                          rows="3"
                          minlength="5"
                          maxlength="500"
                          placeholder="e.g. Investigating customer support ticket KFK-1234..."
                          required></textarea>
                <div id="reveal-reason-error" class="text-danger small mt-1" style="display: none;"></div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-warning" id="reveal-reason-confirm-btn">
                    🔓 Show sensitive data
                </button>
            </div>
        </div>
    </div>
</div>
