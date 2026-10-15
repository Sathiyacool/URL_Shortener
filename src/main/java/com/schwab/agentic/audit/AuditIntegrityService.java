package com.schwab.agentic.audit;

import com.schwab.agentic.orchestration.api.RunAuditVerificationResponse;
import com.schwab.agentic.orchestration.engine.OrchestrationRecordNotFoundException;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIntegrityService {

    private final ExecutionRunRepository runRepository;
    private final ExecutionEventRepository eventRepository;
    private final AuditHashService hashService;

    public AuditIntegrityService(
            ExecutionRunRepository runRepository,
            ExecutionEventRepository eventRepository,
            AuditHashService hashService) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.hashService = hashService;
    }

    @Transactional(readOnly = true)
    public RunAuditVerificationResponse verifyRun(UUID runId) {
        if (!runRepository.existsById(runId)) {
            throw new OrchestrationRecordNotFoundException("Run not found: " + runId);
        }
        var run = runRepository.findById(runId).orElseThrow();
        var events = eventRepository.findAllByRunIdOrderBySequenceNumberAsc(runId);
        String expectedPreviousHash = AuditHashService.GENESIS_HASH;
        long expectedSequence = 1;
        for (var event : events) {
            if (event.getSequenceNumber() != expectedSequence
                    || !expectedPreviousHash.equals(event.getPreviousHash())) {
                return new RunAuditVerificationResponse(
                        runId,
                        false,
                        events.size(),
                        expectedSequence,
                        "Audit sequence or previous-hash link is invalid",
                        expectedPreviousHash);
            }
            String expectedHash = hashService.hash(event);
            if (!expectedHash.equals(event.getEventHash())) {
                return new RunAuditVerificationResponse(
                        runId,
                        false,
                        events.size(),
                        expectedSequence,
                        "Audit event content does not match its stored hash",
                        expectedPreviousHash);
            }
            expectedPreviousHash = event.getEventHash();
            expectedSequence++;
        }
        if (run.getAuditSequence() != events.size()
                || !expectedPreviousHash.equals(run.getAuditHash())) {
            return new RunAuditVerificationResponse(
                    runId,
                    false,
                    events.size(),
                    expectedSequence,
                    "Audit chain does not match the run's stored integrity anchor",
                    expectedPreviousHash);
        }
        return new RunAuditVerificationResponse(
                runId,
                true,
                events.size(),
                null,
                "Audit hash chain is valid",
                expectedPreviousHash);
    }
}
