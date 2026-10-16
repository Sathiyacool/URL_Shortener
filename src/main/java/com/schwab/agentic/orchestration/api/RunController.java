package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import com.schwab.agentic.orchestration.replan.ReplanRequest;
import com.schwab.agentic.orchestration.replan.ReplanResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
@Tag(name = "Orchestration Runs", description = "Create, execute, inspect, stop, and replan DAG runs.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Authentication required in production"),
    @ApiResponse(responseCode = "403", description = "Operator scope required in production")
})
public class RunController {

    private final OrchestrationRunService runService;

    public RunController(OrchestrationRunService runService) {
        this.runService = runService;
    }

    @PostMapping
    @Operation(summary = "Create a run", description = "Validates and persists a run and its dependency DAG.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Run created"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request or dependency graph",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Run state conflict",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<RunView> create(@Valid @RequestBody CreateRunRequest request) {
        UUID runId = runService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(runService.get(runId));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get a run and task states")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Run returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RunView get(@PathVariable UUID runId) {
        return runService.get(runId);
    }

    @GetMapping("/{runId}/audit")
    @Operation(summary = "Export run audit history", description = "Returns persisted execution events and decisions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit history returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RunAuditResponse audit(@PathVariable UUID runId) {
        return runService.audit(runId);
    }

    @GetMapping("/{runId}/audit/verify")
    @Operation(summary = "Verify the run audit hash chain")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Integrity verification result returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RunAuditVerificationResponse verifyAudit(@PathVariable UUID runId) {
        return runService.verifyAudit(runId);
    }

    @GetMapping("/{runId}/approvals/pending")
    @Operation(summary = "List pending approvals for a run")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending approvals returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public List<com.schwab.agentic.orchestration.approval.ApprovalResponse> pendingApprovals(
            @PathVariable UUID runId) {
        return runService.pendingApprovals(runId);
    }

    @PostMapping("/{runId}/execute")
    @Operation(
            summary = "Execute a run",
            description = "Starts or continues execution. If omitted, execution mode defaults to PARALLEL.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution summary returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Run cannot execute from its current state",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ExecutionSummary execute(
            @PathVariable UUID runId, @Valid @RequestBody(required = false) ExecuteRunRequest request) {
        return runService.execute(runId, request == null ? ExecutionMode.PARALLEL : request.mode());
    }

    @PostMapping("/{runId}/resume")
    @Operation(
            summary = "Resume a safe-stopped or approval-paused run",
            description = "Continues a run only from SAFE_STOPPED or AWAITING_APPROVAL.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution summary returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Run cannot be resumed from its current state",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ExecutionSummary resume(
            @PathVariable UUID runId, @Valid @RequestBody(required = false) ExecuteRunRequest request) {
        return runService.resume(runId, request == null ? ExecutionMode.PARALLEL : request.mode());
    }

    @PostMapping("/{runId}/safe-stop")
    @Operation(summary = "Request a cooperative safe-stop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Safe-stop requested"),
        @ApiResponse(
                responseCode = "404",
                description = "Run not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RunView requestSafeStop(@PathVariable UUID runId) {
        return runService.requestSafeStop(runId);
    }

    @PostMapping("/{runId}/replan")
    @Operation(
            summary = "Replan a run",
            description = "Validates a replacement DAG and invalidates only affected downstream tasks.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replan result returned"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid replacement DAG or request",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Run cannot be replanned in its current state",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ReplanResult replan(@PathVariable UUID runId, @Valid @RequestBody ReplanRequest request) {
        return runService.replan(runId, request);
    }
}
