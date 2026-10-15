package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import java.util.Locale;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ScenarioCliRunner implements ApplicationRunner {

    private final ScenarioService scenarioService;
    private final ScenarioLifecycleService lifecycleService;
    private final ConfigurableApplicationContext applicationContext;

    public ScenarioCliRunner(
            ScenarioService scenarioService,
            ScenarioLifecycleService lifecycleService,
            ConfigurableApplicationContext applicationContext) {
        this.lifecycleService = lifecycleService;
        this.scenarioService = scenarioService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("scenario")) {
            return;
        }
        String scenarioId = requiredOption(arguments, "scenario");
        String modeName = arguments.containsOption("mode")
                ? requiredOption(arguments, "mode")
                : ExecutionMode.PARALLEL.name();
        ExecutionMode mode;
        try {
            mode = ExecutionMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported execution mode: " + modeName);
        }

        ScenarioLifecycleService.LifecycleOptions lifecycle = lifecycleOptions(arguments);
        ScenarioRunResponse result = scenarioService.run(scenarioId, mode);
        System.out.println("Scenario " + result.scenarioId() + " run " + result.runId() + " status=" + result.status());
        result.pendingApprovals().forEach(approval -> System.out.println(
                "Pending approval " + approval.approvalId() + " for task " + approval.taskId()));
        if (lifecycle != null) {
            var outcome = lifecycleService.drive(result, mode, lifecycle);
            outcome.steps().forEach(step -> System.out.println("Lifecycle: " + step));
            System.out.println("Scenario " + result.scenarioId() + " run " + outcome.runId()
                    + " final status=" + outcome.finalStatus());
        } else if (!result.pendingApprovals().isEmpty()) {
            System.out.println("Approve through POST /api/approvals/{approvalId}, then resume with POST /api/runs/"
                    + result.runId() + "/resume, or rerun with --decision=APPROVE|REJECT --approver=<name>.");
        }
        if (isNonWebInvocation(arguments)) {
            applicationContext.close();
        }
    }

    private ScenarioLifecycleService.LifecycleOptions lifecycleOptions(ApplicationArguments arguments) {
        if (!arguments.containsOption("decision")) {
            if (arguments.containsOption("safe-stop-before-resume")) {
                throw new IllegalArgumentException("--safe-stop-before-resume requires --decision=APPROVE");
            }
            return null;
        }
        String decisionName = requiredOption(arguments, "decision");
        ApprovalDecision decision;
        try {
            decision = ApprovalDecision.valueOf(decisionName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported decision: " + decisionName);
        }
        boolean safeStop = arguments.containsOption("safe-stop-before-resume");
        if (safeStop && decision != ApprovalDecision.APPROVE) {
            throw new IllegalArgumentException("--safe-stop-before-resume requires --decision=APPROVE");
        }
        String rationale = arguments.containsOption("rationale")
                ? requiredOption(arguments, "rationale")
                : "Decision supplied explicitly by the CLI operator";
        return new ScenarioLifecycleService.LifecycleOptions(
                decision, requiredOption(arguments, "approver"), rationale, safeStop);
    }

    private boolean isNonWebInvocation(ApplicationArguments arguments) {
        List<String> webApplicationTypes = arguments.getOptionValues("spring.main.web-application-type");
        return webApplicationTypes != null && webApplicationTypes.stream().anyMatch("none"::equalsIgnoreCase);
    }

    private String requiredOption(ApplicationArguments arguments, String option) {
        List<String> values = arguments.getOptionValues(option);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("--" + option + " requires a value");
        }
        return values.getFirst();
    }
}
