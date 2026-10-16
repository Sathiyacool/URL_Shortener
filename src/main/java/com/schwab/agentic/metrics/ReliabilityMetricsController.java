package com.schwab.agentic.metrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@Tag(name = "Reliability Metrics", description = "Aggregated orchestration reliability indicators.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Authentication required in production"),
    @ApiResponse(responseCode = "403", description = "Operator scope required in production")
})
public class ReliabilityMetricsController {

    private final ReliabilityMetricsService metricsService;

    public ReliabilityMetricsController(ReliabilityMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/reliability")
    @Operation(summary = "Get reliability metrics")
    @ApiResponse(responseCode = "200", description = "Reliability metrics returned")
    public ReliabilityMetricsResponse reliability() {
        return metricsService.calculate();
    }
}
