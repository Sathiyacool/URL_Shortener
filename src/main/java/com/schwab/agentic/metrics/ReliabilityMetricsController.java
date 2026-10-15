package com.schwab.agentic.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class ReliabilityMetricsController {

    private final ReliabilityMetricsService metricsService;

    public ReliabilityMetricsController(ReliabilityMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/reliability")
    public ReliabilityMetricsResponse reliability() {
        return metricsService.calculate();
    }
}
