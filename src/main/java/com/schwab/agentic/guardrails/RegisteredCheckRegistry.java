package com.schwab.agentic.guardrails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RegisteredCheckRegistry {

    private final Map<RegisteredCheckId, RegisteredCheck> checks;

    public RegisteredCheckRegistry(List<RegisteredCheck> checks) {
        Map<RegisteredCheckId, RegisteredCheck> registered = new HashMap<>();
        for (RegisteredCheck check : checks) {
            if (check.id() == null || registered.putIfAbsent(check.id(), check) != null) {
                throw new IllegalStateException("Registered check IDs must be non-null and unique: " + check.id());
            }
        }
        this.checks = Map.copyOf(registered);
    }

    public boolean isRegistered(RegisteredCheckId id) {
        return id != null && checks.containsKey(id);
    }

    public RegisteredCheckResult run(RegisteredCheckId id) throws Exception {
        RegisteredCheck check = checks.get(id);
        if (check == null) {
            throw new GuardrailViolationException("No implementation is registered for check " + id);
        }
        return check.run();
    }
}
