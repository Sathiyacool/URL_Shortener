package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.audit.AuditIntegrityService;
import com.schwab.agentic.orchestration.approval.ApprovalResponse;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.engine.ExecutionEngine;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.engine.OrchestrationRecordNotFoundException;
import com.schwab.agentic.orchestration.engine.SafeStopController;
import com.schwab.agentic.orchestration.replan.DynamicReplanService;
import com.schwab.agentic.orchestration.replan.ReplanRequest;
import com.schwab.agentic.orchestration.replan.ReplanResult;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrchestrationRunService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrchestrationRunService.class);

    private final DagValidator dagValidator;
    private final OrchestrationPersistenceService persistenceService;
    private final ExecutionEngine executionEngine;
    private final DynamicReplanService replanService;
    private final SafeStopController safeStopController;
    private final ApprovalService approvalService;
    private final ExecutionRunRepository runRepository;
    private final TaskExecutionRepository taskRepository;
    private final ExecutionEventRepository eventRepository;
    private final DecisionRecordRepository decisionRepository;
    private final AuditIntegrityService auditIntegrityService;

    public OrchestrationRunService(
            DagValidator dagValidator,
            OrchestrationPersistenceService persistenceService,
            ExecutionEngine executionEngine,
            DynamicReplanService replanService,
            SafeStopController safeStopController,
            ApprovalService approvalService,
            ExecutionRunRepository runRepository,
            TaskExecutionRepository taskRepository,
            ExecutionEventRepository eventRepository,
            DecisionRecordRepository decisionRepository,
            AuditIntegrityService auditIntegrityService) {
        this.dagValidator = dagValidator;
        this.persistenceService = persistenceService;
        this.executionEngine = executionEngine;
        this.replanService = replanService;
        this.safeStopController = safeStopController;
        this.approvalService = approvalService;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.decisionRepository = decisionRepository;
        this.auditIntegrityService = auditIntegrityService;
    }

    public UUID create(CreateRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Run request is required");
        }
        return persistenceService.createRun(
                request.scenarioId(),
                request.inputSummary(),
                dagValidator.validate(request.tasks()));
    }

    public ExecutionSummary execute(UUID runId, ExecutionMode mode) {
        return executionEngine.execute(runId, mode);
    }

    @Transactional(readOnly = true)
    public RunView get(UUID runId) {
        ExecutionRunEntity run = findRun(runId);
        List<TaskView> tasks = taskRepository.findAllByRunIdOrderByTaskIdAsc(runId).stream()
                .map(TaskView::from)
                .toList();
        return RunView.from(run, tasks);
    }

    @Transactional(readOnly = true)
    public RunAuditResponse audit(UUID runId) {
        findRun(runId);
        return new RunAuditResponse(
                runId,
                eventRepository.findAllByRunIdOrderBySequenceNumberAsc(runId).stream()
                        .map(ExecutionEventView::from)
                        .toList(),
                decisionRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
                        .map(DecisionView::from)
                        .toList());
    }

    public RunAuditVerificationResponse verifyAudit(UUID runId) {
        return auditIntegrityService.verifyRun(runId);
    }

    public ReplanResult replan(UUID runId, ReplanRequest request) {
        return replanService.replan(runId, request);
    }

    public RunView requestSafeStop(UUID runId) {
        findRun(runId);
        safeStopController.requestRunStop(runId);
        log.warn("Safe-stop requested runId={}", runId);
        return get(runId);
    }

    public ExecutionSummary resume(UUID runId, ExecutionMode mode) {
        ExecutionRunEntity run = findRun(runId);
        if (run.getStatus() != com.schwab.agentic.orchestration.domain.RunStatus.SAFE_STOPPED
                && run.getStatus() != com.schwab.agentic.orchestration.domain.RunStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Only safe-stopped or approval-paused runs can be resumed");
        }
        safeStopController.clearRunStop(runId);
        log.info("Resuming runId={} from={} mode={}", runId, run.getStatus(), mode);
        return executionEngine.execute(runId, mode);
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> pendingApprovals(UUID runId) {
        findRun(runId);
        return approvalService.pendingApprovals().stream()
                .filter(request -> request.getRunId().equals(runId))
                .map(ApprovalResponse::from)
                .toList();
    }

    private ExecutionRunEntity findRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
    }
}
