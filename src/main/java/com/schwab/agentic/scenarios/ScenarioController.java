package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "Assessment Scenarios", description = "Run the greenfield, brownfield, and ambiguous demonstrations.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Authentication required in production"),
    @ApiResponse(responseCode = "403", description = "Operator scope required in production")
})
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    @Operation(summary = "List available scenarios")
    @ApiResponse(responseCode = "200", description = "Scenario catalog returned")
    public List<ScenarioDescriptor> list() {
        return scenarioService.list();
    }

    @PostMapping("/{scenarioId}/run")
    @Operation(
            summary = "Run an assessment scenario",
            description = "Creates and executes a scenario. Execution mode defaults to PARALLEL.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Scenario result returned"),
        @ApiResponse(
                responseCode = "400",
                description = "Unknown scenario or invalid execution mode",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Run could not proceed because of a state conflict",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ScenarioRunResponse run(
            @PathVariable String scenarioId, @Valid @RequestBody(required = false) ScenarioRunRequest request) {
        return scenarioService.run(
                scenarioId, request == null ? ExecutionMode.PARALLEL : request.executionMode());
    }

    public record ScenarioRunRequest(
            @Schema(description = "Execution scheduling mode.", example = "PARALLEL")
                    @NotNull
                    ExecutionMode executionMode) {
    }
}
