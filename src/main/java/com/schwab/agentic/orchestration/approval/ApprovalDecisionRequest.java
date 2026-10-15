package com.schwab.agentic.orchestration.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @NotNull ApprovalDecision decision,
        @NotBlank @Size(max = 128) String approver,
        @NotBlank @Size(max = 4000) String rationale) {
}
