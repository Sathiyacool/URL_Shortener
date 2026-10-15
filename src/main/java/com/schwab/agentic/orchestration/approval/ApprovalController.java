package com.schwab.agentic.orchestration.approval;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public List<ApprovalResponse> pending() {
        return approvalService.pendingApprovals().stream().map(ApprovalResponse::from).toList();
    }

    @PostMapping("/{approvalId}")
    public ResponseEntity<ApprovalResponse> decide(
            @PathVariable UUID approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request,
            Authentication authentication) {
        String approver = authentication != null
                        && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken)
                ? authentication.getName()
                : request.approver();
        ApprovalRequestEntity result = approvalService.decide(
                approvalId, request.decision(), approver, request.rationale());
        return ResponseEntity.ok(ApprovalResponse.from(result));
    }
}
