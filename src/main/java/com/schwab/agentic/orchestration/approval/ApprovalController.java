package com.schwab.agentic.orchestration.approval;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Approvals", description = "Review and decide persisted high-impact task approvals.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Authentication required in production"),
    @ApiResponse(responseCode = "403", description = "Operator scope required in production")
})
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending approvals")
    @ApiResponse(responseCode = "200", description = "Pending approvals returned")
    public List<ApprovalResponse> pending() {
        return approvalService.pendingApprovals().stream().map(ApprovalResponse::from).toList();
    }

    @PostMapping("/{approvalId}")
    @Operation(
            summary = "Approve or reject a request",
            description = "A production decision is attributed to the authenticated JWT subject.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approval decision recorded"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid decision or request body",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Approval request not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Approval already decided",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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
