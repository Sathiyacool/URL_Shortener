package com.schwab.agentic.orchestration.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @Schema(description = "Human decision for the pending approval.", example = "APPROVE")
        @NotNull ApprovalDecision decision,
        @Schema(
                        description = "Approver name for local/demo requests; production uses the authenticated JWT subject.",
                        example = "reviewer")
        @NotBlank @Size(max = 128) String approver,
        @Schema(description = "Reason for approving or rejecting the change.", example = "Reviewed the impact and regression plan.")
        @NotBlank @Size(max = 4000) String rationale) {
}
