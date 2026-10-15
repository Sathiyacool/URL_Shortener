package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import com.schwab.agentic.orchestration.replan.ReplanRequest;
import com.schwab.agentic.orchestration.replan.ReplanResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final OrchestrationRunService runService;

    public RunController(OrchestrationRunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public ResponseEntity<RunView> create(@Valid @RequestBody CreateRunRequest request) {
        UUID runId = runService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(runService.get(runId));
    }

    @GetMapping("/{runId}")
    public RunView get(@PathVariable UUID runId) {
        return runService.get(runId);
    }

    @GetMapping("/{runId}/audit")
    public RunAuditResponse audit(@PathVariable UUID runId) {
        return runService.audit(runId);
    }

    @GetMapping("/{runId}/audit/verify")
    public RunAuditVerificationResponse verifyAudit(@PathVariable UUID runId) {
        return runService.verifyAudit(runId);
    }

    @GetMapping("/{runId}/approvals/pending")
    public List<com.schwab.agentic.orchestration.approval.ApprovalResponse> pendingApprovals(
            @PathVariable UUID runId) {
        return runService.pendingApprovals(runId);
    }

    @PostMapping("/{runId}/execute")
    public ExecutionSummary execute(
            @PathVariable UUID runId, @Valid @RequestBody(required = false) ExecuteRunRequest request) {
        return runService.execute(runId, request == null ? ExecutionMode.PARALLEL : request.mode());
    }

    @PostMapping("/{runId}/resume")
    public ExecutionSummary resume(
            @PathVariable UUID runId, @Valid @RequestBody(required = false) ExecuteRunRequest request) {
        return runService.resume(runId, request == null ? ExecutionMode.PARALLEL : request.mode());
    }

    @PostMapping("/{runId}/safe-stop")
    public RunView requestSafeStop(@PathVariable UUID runId) {
        return runService.requestSafeStop(runId);
    }

    @PostMapping("/{runId}/replan")
    public ReplanResult replan(@PathVariable UUID runId, @Valid @RequestBody ReplanRequest request) {
        return runService.replan(runId, request);
    }
}
