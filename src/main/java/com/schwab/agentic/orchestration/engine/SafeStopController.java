package com.schwab.agentic.orchestration.engine;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class SafeStopController {

    private final AtomicBoolean globalStopRequested = new AtomicBoolean();
    private final Set<UUID> stoppedRuns = ConcurrentHashMap.newKeySet();

    public void requestGlobalStop() {
        globalStopRequested.set(true);
    }

    public void clearGlobalStop() {
        globalStopRequested.set(false);
    }

    public void requestRunStop(UUID runId) {
        stoppedRuns.add(runId);
    }

    public void clearRunStop(UUID runId) {
        stoppedRuns.remove(runId);
    }

    public boolean isStopRequested(UUID runId) {
        return globalStopRequested.get() || stoppedRuns.contains(runId);
    }
}
